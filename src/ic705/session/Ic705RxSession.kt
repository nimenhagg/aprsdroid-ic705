package org.aprsdroid.app.ic705.session

import android.util.Log

import java.io.Closeable
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.audio.Afsk1200PcmGenerator
import org.aprsdroid.app.audio.Ax25PacketEncoder
import org.aprsdroid.app.ic705.protocol.Ic705CivDatagram
import org.aprsdroid.app.ic705.protocol.Ic705CivDatagramCodec
import org.aprsdroid.app.ic705.session.Ic705PttActions
import org.aprsdroid.app.ic705.session.Ic705PttState
import org.aprsdroid.app.ic705.session.Ic705PttStateMachine
import org.aprsdroid.app.ic705.session.Ic705TxAudioPacketizer

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.EnumMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705CivChannelAction
import org.aprsdroid.app.ic705.protocol.Ic705CivOpenClosePacket
import org.aprsdroid.app.ic705.protocol.Ic705ConnectionInfoAnnouncement
import org.aprsdroid.app.ic705.protocol.Ic705ConnectionInfoCodec
import org.aprsdroid.app.ic705.protocol.Ic705ConnectionParameters
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacket
import org.aprsdroid.app.ic705.protocol.Ic705ControlPacketCodec
import org.aprsdroid.app.ic705.protocol.Ic705HandshakeCodec
import org.aprsdroid.app.ic705.protocol.Ic705PingPacket
import org.aprsdroid.app.ic705.protocol.Ic705ProtocolException
import org.aprsdroid.app.ic705.transport.Ic705ChannelRole
import org.aprsdroid.app.ic705.transport.Ic705DatagramChannel
import org.aprsdroid.app.ic705.transport.Ic705DatagramChannelFactory
import org.aprsdroid.app.ic705.transport.Ic705DatagramSocketFactory
import org.aprsdroid.app.ic705.transport.Ic705ReceivedDatagram
import org.aprsdroid.app.ic705.transport.Ic705UdpChannel

/**
 * Runtime coordinator for an IC-705 receive-only LAN session.
 *
 * It sends only the control traffic required to authenticate and open receive
 * streams. There is intentionally no PTT, CI-V business-command, or audio-TX API.
 */
class Ic705RxSession internal constructor(
    private val config: Ic705RxSessionConfig,
    private val audioSink: PcmSink,
    private val callbacks: Ic705RxSessionCallbacks,
    private val channelFactory: Ic705DatagramChannelFactory,
    private val controlExecutor: ScheduledExecutorService,
    private val audioExecutor: ThreadPoolExecutor,
    private val randomInt: () -> Int,
    private val monotonicMillis: () -> Long,
    private val wireProfile: Ic705RxWireProfile = Ic705RxWireProfile.WFVIEW,
) : Ic705RadioSession {
    constructor(
        config: Ic705RxSessionConfig,
        audioSink: PcmSink,
        callbacks: Ic705RxSessionCallbacks = Ic705RxSessionCallbacks(),
        socketFactory: Ic705DatagramSocketFactory,
    ) : this(
        config = config,
        audioSink = audioSink,
        callbacks = callbacks,
        channelFactory = Ic705DatagramChannelFactory { role, localAddress, onDatagram, onError ->
            Ic705UdpChannel(
                role = role,
                localAddress = localAddress,
                socketFactory = socketFactory,
                onDatagram = onDatagram,
                onError = onError,
            )
        },
        controlExecutor = Executors.newSingleThreadScheduledExecutor(
            namedDaemonThreadFactory("IC-705 RX session"),
        ),
        audioExecutor = newAudioExecutor(),
        randomInt = SecureRandom()::nextInt,
        monotonicMillis = { System.nanoTime() / 1_000_000L },
    )

    internal constructor(
        config: Ic705RxSessionConfig,
        audioSink: PcmSink,
        callbacks: Ic705RxSessionCallbacks,
        socketFactory: Ic705DatagramSocketFactory,
        wireProfile: Ic705RxWireProfile,
    ) : this(
        config = config,
        audioSink = audioSink,
        callbacks = callbacks,
        channelFactory = Ic705DatagramChannelFactory { role, localAddress, onDatagram, onError ->
            Ic705UdpChannel(
                role = role,
                localAddress = localAddress,
                socketFactory = socketFactory,
                onDatagram = onDatagram,
                onError = onError,
            )
        },
        controlExecutor = Executors.newSingleThreadScheduledExecutor(
            namedDaemonThreadFactory("IC-705 RX session"),
        ),
        audioExecutor = newAudioExecutor(),
        randomInt = SecureRandom()::nextInt,
        monotonicMillis = { System.nanoTime() / 1_000_000L },
        wireProfile = wireProfile,
    )

    constructor(
        config: Ic705RxSessionConfig,
        audioSink: PcmSink,
        callbacks: Ic705RxSessionCallbacks = Ic705RxSessionCallbacks(),
    ) : this(
        config = config,
        audioSink = audioSink,
        callbacks = callbacks,
        channelFactory = Ic705DatagramChannelFactory { role, localAddress, onDatagram, onError ->
            Ic705UdpChannel(
                role = role,
                localAddress = localAddress,
                onDatagram = onDatagram,
                onError = onError,
            )
        },
        controlExecutor = Executors.newSingleThreadScheduledExecutor(
            namedDaemonThreadFactory("IC-705 RX session"),
        ),
        audioExecutor = newAudioExecutor(),
        randomInt = SecureRandom()::nextInt,
        monotonicMillis = { System.nanoTime() / 1_000_000L },
    )

    private data class ChannelRuntime(
        val role: Ic705ChannelRole,
        var localId: Int,
        val trackedPackets: Ic705TrackedPacketStore,
        var remoteId: Int? = null,
        var pingSequence: Int = 0,
        var civSequence: Int = 0,
        val lastReceivedAtMillis: AtomicLong,
    )

    private data class StreamRecoveryState(
        val attempt: Int,
        val baselineRxMillis: Long,
        val deadlineMillis: Long,
    )

    private val closed = AtomicBoolean(false)
    private val closeMonitor = Any()
    private val pendingCloseCallbacks = mutableListOf<() -> Unit>()
    private var closeComplete = false
    private val audioLifecycleLock = Any()
    private val channels: MutableMap<Ic705ChannelRole, Ic705DatagramChannel> =
        java.util.Collections.synchronizedMap(EnumMap<Ic705ChannelRole, Ic705DatagramChannel>(Ic705ChannelRole::class.java))
    private val channelRuntimes: MutableMap<Ic705ChannelRole, ChannelRuntime> =
        java.util.Collections.synchronizedMap(EnumMap<Ic705ChannelRole, ChannelRuntime>(Ic705ChannelRole::class.java))
    private val scheduledTasks = Ic705SessionTaskRegistry()
    private val streamRecoveries =
        EnumMap<Ic705ChannelRole, StreamRecoveryState>(Ic705ChannelRole::class.java)

    private val txExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ic705-tx").apply { isDaemon = true }
    }
    private val afskPcmGenerator = Afsk1200PcmGenerator(sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ)
    private var nextTxOuterSequence = 1
    private var nextTxAudioSequence = 1
    @Volatile
    private var lastTxCompletedMonotonicMillis = 0L

    @Volatile
    private var audioWatchdogGraceUntilMillis = 0L

    private val pttStateMachine: Ic705PttStateMachine = Ic705PttStateMachine(
        actions = object : Ic705PttActions {
            override fun sendCivFrame(frame: ByteArray) {
                val runtime = checkNotNull(channelRuntimes[Ic705ChannelRole.CIV]) {
                    "CI-V channel is unavailable"
                }
                val remoteId = checkNotNull(runtime.remoteId) {
                    "CI-V remote endpoint is unavailable"
                }
                val civEnvelope = Ic705CivDatagramCodec.encode(
                    Ic705CivDatagram(
                        type = 0,
                        sequence = 0,
                        senderId = runtime.localId,
                        receiverId = remoteId,
                        civSequence = nextCivSequence(runtime),
                        civFrame = frame,
                    )
                )
                sendTracked(Ic705ChannelRole.CIV, civEnvelope)
            }

            override fun sendAudioDatagram(datagram: ByteArray) {
                sendUntracked(Ic705ChannelRole.AUDIO, datagram)
            }

            override fun onStateChanged(state: Ic705PttState) {
                if (state == Ic705PttState.RX_IDLE) {
                    audioWatchdogGraceUntilMillis =
                        monotonicMillis() + config.timing.audioPostTxGraceMillis
                }
            }
        },
        watchdogExecutor = controlExecutor,
    )

    @Volatile
    private var engineState = Ic705RxSessionEngine.State()

    @Volatile
    private var generation = 0L

    @Volatile
    private var audioReceiver: Ic705RxAudioReceiver? = null

    private var localToken = 0
    private var radioToken = 0
    private var authInnerSequence = wireProfile.initialAuthInnerSequence
    private var connectionAnnouncement: Ic705ConnectionInfoAnnouncement? = null
    private var discoveredRadioAddress: InetAddress? = null
    @Volatile
    private var firstAudioReported = false

    /** Guarded by [audioLifecycleLock]. */
    private var audioWrittenInGeneration = false

    /** Accessed only by the control executor. */
    private var restartResetDeliveredByClose = false

    override val state: Ic705RxSessionEngine.State
        get() = engineState

    override fun start() {
        check(!closed.get()) { "IC-705 RX session is closed" }
        controlExecutor.execute { dispatch(Ic705RxSessionEngine.Event.Start) }
    }

    override fun stop() {
        if (closed.get()) return
        controlExecutor.execute { dispatch(Ic705RxSessionEngine.Event.Stop) }
    }

    override val isTransmitting: Boolean
        get() = pttStateMachine.isTransmitting

    override fun transmit(packet: APRSPacket): Boolean {
        synchronized(audioLifecycleLock) {
            val now = monotonicMillis()
            val cooldownRemaining = TX_COOLDOWN_GUARD_MILLIS - (now - lastTxCompletedMonotonicMillis)
            if (cooldownRemaining > 0) {
                Log.w(TAG, "transmit() rejected by cooldown guard (${cooldownRemaining}ms remaining)")
                return false
            }
            if (engineState.phase != Ic705RxSessionEngine.Phase.RECEIVING) {
                Log.w(TAG, "transmit() rejected: engine phase is ${engineState.phase}")
                return false
            }
            if (pttStateMachine.isTransmitting) {
                Log.w(TAG, "transmit() rejected: ptt is already transmitting")
                return false
            }
            if (closed.get()) {
                Log.w(TAG, "transmit() rejected: session is closed")
                return false
            }
            val audioRuntime = channelRuntimes[Ic705ChannelRole.AUDIO]
            val audioRemoteId = audioRuntime?.remoteId
            val civRuntime = channelRuntimes[Ic705ChannelRole.CIV]
            val civRemoteId = civRuntime?.remoteId
            if (audioRuntime == null || audioRemoteId == null || civRuntime == null || civRemoteId == null) {
                Log.w(TAG, "transmit() rejected: audio or civ runtime/remoteId is null (audio=$audioRemoteId, civ=$civRemoteId)")
                return false
            }

            val ax25 = Ax25PacketEncoder.encode(packet)
            val samples = afskPcmGenerator.generateSamples(ax25)
            val packetizer = Ic705TxAudioPacketizer(
                senderId = audioRuntime.localId,
                receiverId = audioRemoteId,
                initialOuterSequence = nextTxOuterSequence,
                initialAudioSequence = nextTxAudioSequence,
            )
            val datagrams = packetizer.packetize(samples)
            
            nextTxOuterSequence = packetizer.outerSequence
            nextTxAudioSequence = packetizer.audioSequence

            val started = try {
                pttStateMachine.beginTransmission()
            } catch (error: Exception) {
                Log.e(TAG, "transmit() failed to send PTT ON", error)
                reportIssue(Ic705RxSessionIssueCode.SOCKET_IO, Ic705ChannelRole.CIV)
                return false
            }
            if (!started) {
                Log.w(TAG, "transmit() pttStateMachine.beginTransmission() returned false")
                return false
            }
            startTxAudioStreaming(datagrams)
            return true
        }
    }

    private fun startTxAudioStreaming(datagrams: List<ByteArray>) {
        pttStateMachine.onAudioStreamingStarted()
        try {
            txExecutor.execute {
                try {
                    val samplesPerPacket = Ic705AudioPacketCodec.SAMPLES_PER_PACKET
                    val sampleRateHz = Ic705AudioPacketCodec.SAMPLE_RATE_HZ
                    val packetDurationNs = (samplesPerPacket.toLong() * 1_000_000_000L) / sampleRateHz
                    // 60ms Pre-roll lead cushion fills IC-705 DSP jitter buffer to prevent underruns
                    val leadNs = 60_000_000L
                    val startNs = System.nanoTime()

                    for ((index, datagram) in datagrams.withIndex()) {
                        if (!pttStateMachine.canStreamAudio || closed.get() || engineState.phase != Ic705RxSessionEngine.Phase.RECEIVING) {
                            Log.w(TAG, "startTxAudioStreaming: aborted at packet $index (canStreamAudio=${pttStateMachine.canStreamAudio}, pttState=${pttStateMachine.state}, closed=${closed.get()}, phase=${engineState.phase})")
                            break
                        }
                        val targetNs = startNs + (index * packetDurationNs) - leadNs
                        while (true) {
                            val diffNs = targetNs - System.nanoTime()
                            if (diffNs <= 0) break
                            if (diffNs > 3_000_000L) {
                                Thread.sleep(1)
                            } else {
                                Thread.yield()
                            }
                        }
                        val audioChannel = channels[Ic705ChannelRole.AUDIO]
                        if (audioChannel == null || !audioChannel.isOpen) {
                            Log.w(TAG, "startTxAudioStreaming: AUDIO channel closed during transmission at packet $index")
                            break
                        }
                        sendTracked(Ic705ChannelRole.AUDIO, datagram)
                    }

                    if (pttStateMachine.canStreamAudio && !closed.get()) {
                        pttStateMachine.onAudioStreamingFinished()
                        Thread.sleep(TX_DRAIN_WAIT_MILLIS)
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "startTxAudioStreaming: interrupted")
                    Thread.currentThread().interrupt()
                } catch (e: Throwable) {
                    Log.e(TAG, "startTxAudioStreaming: error during TX streaming", e)
                    pttStateMachine.forceRelease("Streaming error: ${e.message}")
                } finally {
                    pttStateMachine.finishTransmission()
                    lastTxCompletedMonotonicMillis = monotonicMillis()
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "startTxAudioStreaming: TX executor rejected execution", e)
            pttStateMachine.forceRelease("TX executor shut down")
            lastTxCompletedMonotonicMillis = monotonicMillis()
        } catch (e: Throwable) {
            Log.e(TAG, "startTxAudioStreaming: unexpected error", e)
            pttStateMachine.forceRelease("Unexpected error: ${e.message}")
            lastTxCompletedMonotonicMillis = monotonicMillis()
        }
    }

    override fun close() {
        close(onClosed = {})
    }

    /** Calls [onClosed] after sockets close and the audio worker has exited. */
    override fun close(onClosed: () -> Unit) {
        var initiateClose = false
        var alreadyComplete = false
        synchronized(closeMonitor) {
            if (closeComplete) {
                alreadyComplete = true
            } else {
                pendingCloseCallbacks += onClosed
                initiateClose = closed.compareAndSet(false, true)
            }
        }
        if (alreadyComplete) {
            safeCallback(onClosed)
            return
        }
        if (!initiateClose) return

        try {
            controlExecutor.execute {
                try {
                    // Interrupt a cooperative sink before closeSockets waits for the
                    // lifecycle barrier held by an in-flight audio write.
                    audioExecutor.shutdownNow()
                    dispatch(Ic705RxSessionEngine.Event.Stop)
                } finally {
                    audioExecutor.shutdownNow()
                    pttStateMachine.shutdown()
                    controlExecutor.shutdown()
                    awaitAudioTermination()
                    finishClose()
                }
            }
        } catch (_: RejectedExecutionException) {
            audioExecutor.shutdownNow()
            pttStateMachine.shutdown()
            controlExecutor.shutdown()
            Thread(
                {
                    awaitAudioTermination()
                    finishClose()
                },
                "IC-705 RX close",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun awaitAudioTermination() {
        var interrupted = false
        while (!audioExecutor.isTerminated) {
            try {
                if (audioExecutor.awaitTermination(CLOSE_WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS)) break
                audioExecutor.shutdownNow()
            } catch (_: InterruptedException) {
                interrupted = true
                audioExecutor.shutdownNow()
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    private fun finishClose() {
        val closeCallbacks = synchronized(closeMonitor) {
            closeComplete = true
            pendingCloseCallbacks.toList().also { pendingCloseCallbacks.clear() }
        }
        txExecutor.shutdownNow()
        closeCallbacks.forEach(::safeCallback)
    }

    private fun dispatch(event: Ic705RxSessionEngine.Event) {
        val transition = Ic705RxSessionEngine.reduce(engineState, event)
        val previousState = engineState
        engineState = transition.state
        if (previousState != transition.state) {
            safeCallback { callbacks.onStateChanged(transition.state) }
            updateStageTimeout(transition.state)
        }
        transition.actions.forEach(::executeAction)
    }

    private fun executeAction(action: Ic705RxSessionEngine.Action) {
        try {
            when (action) {
                Ic705RxSessionEngine.Action.OpenSockets -> openSockets()
                Ic705RxSessionEngine.Action.CloseSockets -> closeSockets(sendProtocolClose = true)
                Ic705RxSessionEngine.Action.SendDiscovery -> startDiscovery(Ic705ChannelRole.CONTROL)
                Ic705RxSessionEngine.Action.SendLogin -> sendLogin()
                is Ic705RxSessionEngine.Action.SendTokenConfirmation -> {
                    sendTokenConfirmation(action.token)
                }
                Ic705RxSessionEngine.Action.ScheduleConnectionInfoSettle -> {
                    scheduleConnectionInfoSettle()
                }
                Ic705RxSessionEngine.Action.SendConnectionInfo -> {
                    sendConnectionInfo()
                }
                Ic705RxSessionEngine.Action.ScheduleConnectionInfoRetry -> {
                    scheduleConnectionInfoRetry()
                }
                Ic705RxSessionEngine.Action.CancelConnectionInfoTimers -> {
                    cancelConnectionInfoTimers()
                }
                is Ic705RxSessionEngine.Action.SendOpenStreams -> openStreams(action.endpoints)
                is Ic705RxSessionEngine.Action.ScheduleRetry -> {
                    scheduleRetry(action.attempt, action.cooldown)
                }
                Ic705RxSessionEngine.Action.CancelRetryTimer -> cancelTask(TASK_RETRY)
                Ic705RxSessionEngine.Action.NotifyAudioDiscontinuity -> {
                    if (restartResetDeliveredByClose) {
                        restartResetDeliveredByClose = false
                    } else {
                        notifyAudioReset(Ic705AudioResetReason.SESSION_RESTART)
                    }
                }
            }
        } catch (_: IOException) {
            reportIssue(Ic705RxSessionIssueCode.SOCKET_IO, null)
            dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("socket I/O"))
        } catch (_: RuntimeException) {
            reportIssue(Ic705RxSessionIssueCode.MALFORMED_PACKET, null)
            dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("session action failed"))
        }
    }

    private fun openSockets() {
        closeSockets(sendProtocolClose = false)
        val currentGeneration = generation
        connectionAnnouncement = null
        discoveredRadioAddress = null
        radioToken = 0
        localToken = if (wireProfile.randomizeTokenRequest) randomInt() and 0xffff else 0
        authInnerSequence = wireProfile.initialAuthInnerSequence
        nextTxOuterSequence = 1
        nextTxAudioSequence = 1
        firstAudioReported = false
        val now = monotonicMillis()

        Ic705ChannelRole.values().forEach { role ->
            val runtime = ChannelRuntime(
                role = role,
                // The Icom LAN client ID is derived only after UDP bind assigns the
                // actual local port. wfview and rigplane both tie this value to the
                // local endpoint; a random ID can leave an IC-705 accepting login but
                // silently ignoring the following connection-info request.
                localId = 0,
                trackedPackets = Ic705TrackedPacketStore(
                    initialSequence = wireProfile.initialTrackedSequence,
                    monotonicMillis = monotonicMillis,
                ),
                lastReceivedAtMillis = AtomicLong(now),
            )
            channelRuntimes[role] = runtime
            channels[role] = channelFactory.create(
                role,
                preferredLocalAddress(role),
                { datagram -> onTransportDatagram(currentGeneration, runtime, datagram) },
                { onTransportError(currentGeneration, role) },
            )
        }

        channels.getValue(Ic705ChannelRole.CONTROL).setRemoteEndpoint(
            InetSocketAddress(config.radioAddress, config.controlPort),
            lockSource = false,
        )
        channels.values.forEach(Ic705DatagramChannel::open)
        channelRuntimes.values.forEach { runtime ->
            val channel = channels.getValue(runtime.role)
            runtime.localId = if (wireProfile.randomizeClientId) {
                // Official RS-BA1 captures assign a fresh independent 32-bit ID to
                // control, CI-V, and audio on every connection, despite fixed ports.
                randomInt().takeUnless { it == 0 } ?: 1
            } else {
                ic705ClientIdForEndpoint(null, channel.localPort)
            }
        }
        startFixedTask(
            key = TASK_WATCHDOG,
            initialDelayMillis = config.timing.watchdogPeriodMillis,
            periodMillis = config.timing.watchdogPeriodMillis,
            expectedGeneration = currentGeneration,
            block = ::checkWatchdog,
        )
        dispatch(Ic705RxSessionEngine.Event.SocketsOpened)
    }

    private fun preferredLocalAddress(role: Ic705ChannelRole): InetSocketAddress {
        val requestedPort = when (role) {
            Ic705ChannelRole.CONTROL -> config.controlPort
            Ic705ChannelRole.CIV -> config.controlPort + 1
            Ic705ChannelRole.AUDIO -> config.controlPort + 2
        }
        // A successful RS-BA1 capture uses symmetric local/remote endpoints for
        // all three channels: 50001 control, 50002 CI-V, and 50003 audio.
        return InetSocketAddress(if (requestedPort in 1..0xffff) requestedPort else 0)
    }

    private fun closeSockets(sendProtocolClose: Boolean) {
        pttStateMachine.forceRelease("Sockets closing")
        cancelAllTasks()
        restartResetDeliveredByClose = false
        val shouldNotifyAudioReset = synchronized(audioLifecycleLock) {
            generation += 1
            audioExecutor.queue.clear()
            val hadAudio = audioWrittenInGeneration
            audioReceiver?.reset()
            audioReceiver = null
            audioWrittenInGeneration = false
            hadAudio
        }
        if (shouldNotifyAudioReset) {
            restartResetDeliveredByClose = true
            safeCallback {
                callbacks.onAudioReset(Ic705AudioReset(Ic705AudioResetReason.SESSION_RESTART))
            }
        }
        if (sendProtocolClose) bestEffortProtocolClose()
        channels.values.forEach { channel -> runCatching(channel::close) }
        channels.clear()
        channelRuntimes.clear()
        streamRecoveries.clear()
        connectionAnnouncement = null
        discoveredRadioAddress = null
        localToken = 0
        radioToken = 0
        authInnerSequence = wireProfile.initialAuthInnerSequence
        firstAudioReported = false
    }

    private fun bestEffortProtocolClose() {
        val civ = channelRuntimes[Ic705ChannelRole.CIV]
        if (civ?.remoteId != null) {
            runCatching {
                sendTracked(
                    Ic705ChannelRole.CIV,
                    Ic705HandshakeCodec.encodeCivOpenClose(
                        Ic705CivOpenClosePacket(
                            sequence = 0,
                            senderId = civ.localId,
                            receiverId = requireNotNull(civ.remoteId),
                            civSequence = nextCivSequence(civ),
                            action = Ic705CivChannelAction.CLOSE,
                        ),
                    ),
                )
            }
        }
        val control = channelRuntimes[Ic705ChannelRole.CONTROL]
        if (control?.remoteId != null && radioToken != 0) {
            runCatching {
                sendTracked(
                    Ic705ChannelRole.CONTROL,
                    Ic705HandshakeCodec.encodeTokenDelete(
                        sequence = 0,
                        senderId = control.localId,
                        receiverId = requireNotNull(control.remoteId),
                        innerSequence = nextAuthInnerSequence(),
                        tokenRequest = localToken,
                        token = radioToken,
                    ),
                )
            }
        }
        channelRuntimes.values.forEach { runtime ->
            if (runtime.remoteId != null) {
                runCatching {
                    sendUntracked(
                        runtime.role,
                        controlPacket(
                            type = Ic705ControlPacketCodec.TYPE_DISCONNECT,
                            sequence = 0,
                            runtime = runtime,
                        ),
                    )
                }
            }
        }
    }

    private fun onTransportDatagram(
        expectedGeneration: Long,
        runtime: ChannelRuntime,
        datagram: Ic705ReceivedDatagram,
    ) {
        val role = runtime.role
        if (role == Ic705ChannelRole.AUDIO && looksLikeIc705Audio(datagram.data)) {
            enqueueAudio(expectedGeneration, runtime, datagram.data)
        } else {
            submitForGeneration(expectedGeneration) { handleDatagram(runtime, datagram) }
        }
    }

    private fun onTransportError(expectedGeneration: Long, role: Ic705ChannelRole) {
        submitForGeneration(expectedGeneration) {
            reportIssue(Ic705RxSessionIssueCode.SOCKET_IO, role)
            dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("$role socket I/O"))
        }
    }

    private fun handleDatagram(runtime: ChannelRuntime, datagram: Ic705ReceivedDatagram) {
        val role = runtime.role
        try {
            if (datagram.data.size == Ic705HandshakeCodec.PING_PACKET_SIZE) {
                // Real radios use a zero declared-length field for ping packets,
                // so they must be decoded before the generic envelope validator.
                handlePing(runtime, datagram.data)
                runtime.lastReceivedAtMillis.set(monotonicMillis())
                return
            }
            validateIc705CommonEnvelope(datagram.data, runtime.localId)
            runtime.lastReceivedAtMillis.set(monotonicMillis())
            when {
                datagram.data.size == Ic705ControlPacketCodec.PACKET_SIZE -> {
                    handleControlPacket(runtime, datagram.data, datagram.source)
                }
                role == Ic705ChannelRole.CONTROL &&
                    datagram.data.size in IC705_AUTHENTICATED_PACKET_SIZES -> {
                    handleControlSessionPacket(datagram.data)
                }
                isIc705VariableRetransmit(datagram.data) -> handleRetransmit(runtime, datagram.data)
                role == Ic705ChannelRole.CIV &&
                    datagram.data.size > Ic705CivDatagramCodec.HEADER_SIZE &&
                    (datagram.data[0x10].toInt() and 0xff) == Ic705CivDatagramCodec.CIV_MARKER -> {
                    handleCivDatagram(runtime, datagram.data)
                }
                else -> Unit
            }
        } catch (_: IOException) {
            reportIssue(Ic705RxSessionIssueCode.SOCKET_IO, role)
            dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("$role socket I/O"))
        } catch (_: Ic705ProtocolException) {
            reportIssue(
                Ic705RxSessionIssueCode.MALFORMED_PACKET,
                role,
                ic705PacketDiagnostic(datagram.data, runtime.localId),
            )
        } catch (_: IllegalArgumentException) {
            reportIssue(
                Ic705RxSessionIssueCode.MALFORMED_PACKET,
                role,
                ic705PacketDiagnostic(datagram.data, runtime.localId),
            )
        }
    }

    private fun handleCivDatagram(runtime: ChannelRuntime, data: ByteArray) {
        val civPacket = Ic705CivDatagramCodec.decode(data, expectedReceiverId = runtime.localId)
        
        
        pttStateMachine.onCivReceived(civPacket.civFrame)
    }

    private fun handleControlPacket(
        runtime: ChannelRuntime,
        data: ByteArray,
        source: InetSocketAddress,
    ) {
        val packet = Ic705ControlPacketCodec.decode(data, expectedReceiverId = runtime.localId)
        when (packet.type) {
            Ic705ControlPacketCodec.TYPE_RETRANSMIT -> handleRetransmit(runtime, data)
            Ic705ControlPacketCodec.TYPE_ARE_YOU_THERE -> {
                sendUntracked(
                    runtime.role,
                    Ic705ControlPacketCodec.encode(
                        Ic705ControlPacket(
                            type = Ic705ControlPacketCodec.TYPE_I_AM_HERE,
                            sequence = packet.sequence,
                            senderId = runtime.localId,
                            receiverId = packet.senderId,
                        ),
                    ),
                )
            }
            Ic705ControlPacketCodec.TYPE_I_AM_HERE -> {
                onChannelDiscovered(runtime, packet.senderId, source)
            }
            Ic705ControlPacketCodec.TYPE_READY -> onChannelReady(runtime)
            Ic705ControlPacketCodec.TYPE_DISCONNECT -> {
                dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("radio disconnected"))
            }
        }
    }

    private fun onChannelDiscovered(
        runtime: ChannelRuntime,
        remoteId: Int,
        source: InetSocketAddress,
    ) {
        if (runtime.remoteId != null && !wireProfile.repeatReadyOnDuplicateDiscovery) return
        runtime.remoteId = remoteId
        channels.getValue(runtime.role).setRemoteEndpoint(source, lockSource = true)
        if (runtime.role == Ic705ChannelRole.CONTROL) discoveredRadioAddress = source.address
        cancelTask(discoveryTask(runtime.role))
        cancelTask(discoveryTimeoutTask(runtime.role))
        if (wireProfile.startPingBeforeReady) startPing(runtime.role)
        sendUntracked(
            runtime.role,
            controlPacket(
                type = Ic705ControlPacketCodec.TYPE_READY,
                sequence = wireProfile.readySequence,
                runtime = runtime,
            ),
        )
        if (runtime.role == Ic705ChannelRole.CONTROL) {
            dispatch(Ic705RxSessionEngine.Event.ControlDiscovered)
        } else if (runtime.role == Ic705ChannelRole.AUDIO) {
            val receiverGeneration = generation
            synchronized(audioLifecycleLock) {
                if (generation != receiverGeneration || closed.get()) return
                audioReceiver = Ic705RxAudioReceiver(
                localId = runtime.localId,
                radioId = remoteId,
                sink = audioSink,
                onDiscontinuity = { discontinuity ->
                    // This callback runs before the current post-gap PCM is written,
                    // allowing the downstream demodulator to reset synchronously.
                    if (generation == receiverGeneration && !closed.get()) {
                        safeCallback {
                            callbacks.onAudioReset(
                                Ic705AudioReset(
                                    Ic705AudioResetReason.UDP_DISCONTINUITY,
                                    discontinuity,
                                ),
                            )
                        }
                    }
                },
                )
            }
        }
    }

    private fun onChannelReady(runtime: ChannelRuntime) {
        when (runtime.role) {
            Ic705ChannelRole.CONTROL -> {
                if (!wireProfile.startPingBeforeReady) startPing(runtime.role)
                if (wireProfile.sendTrackedIdle) startIdle(runtime.role)
                dispatch(Ic705RxSessionEngine.Event.ControlReady)
            }
            Ic705ChannelRole.CIV -> {
                sendTracked(
                    runtime.role,
                    Ic705HandshakeCodec.encodeCivOpenClose(
                        Ic705CivOpenClosePacket(
                            sequence = 0,
                            senderId = runtime.localId,
                            receiverId = requireNotNull(runtime.remoteId),
                            civSequence = nextCivSequence(runtime),
                            action = Ic705CivChannelAction.OPEN,
                        ),
                    ),
                )
                if (wireProfile.sendTrackedIdle) startIdle(runtime.role)
                dispatch(Ic705RxSessionEngine.Event.CivReady)
            }
            Ic705ChannelRole.AUDIO -> dispatch(Ic705RxSessionEngine.Event.AudioReady)
        }
    }

    private fun handlePing(runtime: ChannelRuntime, data: ByteArray) {
        val packet = Ic705HandshakeCodec.decodePing(data, expectedReceiverId = runtime.localId)
        if (packet.isReply) {
            if (packet.sequence == runtime.pingSequence) {
                runtime.pingSequence = (runtime.pingSequence + 1) and 0xffff
            }
        } else {
            sendUntracked(
                runtime.role,
                Ic705HandshakeCodec.encodePing(
                    packet.copy(
                        senderId = runtime.localId,
                        receiverId = packet.senderId,
                        isReply = true,
                    ),
                ),
            )
        }
    }

    private fun handleControlSessionPacket(data: ByteArray) {
        val control = channelRuntimes.getValue(Ic705ChannelRole.CONTROL)
        when (data.size) {
            Ic705HandshakeCodec.LOGIN_RESPONSE_PACKET_SIZE -> {
                val response = Ic705HandshakeCodec.decodeLoginResponse(data, control.localId)
                if (response.isAuthenticated) {
                    radioToken = response.header.token
                    dispatch(Ic705RxSessionEngine.Event.LoginAccepted(radioToken))
                } else {
                    dispatch(Ic705RxSessionEngine.Event.LoginRejected("authentication rejected"))
                }
            }
            Ic705HandshakeCodec.TOKEN_PACKET_SIZE -> {
                val tokenPacket = Ic705HandshakeCodec.decodeTokenPacket(data, control.localId)
                if (
                    tokenPacket.header.requestType == Ic705HandshakeCodec.TOKEN_REQUEST_RENEWAL &&
                    tokenPacket.header.requestReply == Ic705HandshakeCodec.REQUEST_REPLY_RESPONSE
                ) {
                    when (tokenPacket.responseCode) {
                        0 -> Unit
                        -1 -> {
                            control.remoteId = tokenPacket.header.senderId
                            localToken = tokenPacket.header.tokenRequest
                            radioToken = tokenPacket.header.token
                            if (engineState.connectionRequestAuthorized) {
                                // A later -1 invalidates the established authorization.
                                // Reusing the old reducer facts would leave the UI in
                                // RECEIVING without reopening potentially new stream ports.
                                dispatch(
                                    Ic705RxSessionEngine.Event.RecoverableFailure(
                                        "token reauthorization required",
                                    ),
                                )
                            } else {
                                dispatch(Ic705RxSessionEngine.Event.ConnectionRequestAuthorized)
                            }
                        }
                        else -> dispatch(
                            Ic705RxSessionEngine.Event.RecoverableFailure("token renewal rejected"),
                        )
                    }
                }
            }
            Ic705ConnectionInfoCodec.PACKET_SIZE -> {
                val announcement = Ic705ConnectionInfoCodec.decodeAnnouncement(data, control.localId)
                if (!announcement.isBusy) {
                    connectionAnnouncement = announcement
                    control.remoteId = announcement.header.senderId
                    localToken = announcement.header.tokenRequest
                    radioToken = announcement.header.token
                    // Preserve the fact first so the reducer suppresses a redundant blank 0x90.
                    dispatch(Ic705RxSessionEngine.Event.ConnectionInfoReceived)
                    if (!engineState.connectionRequestAuthorized) {
                        dispatch(Ic705RxSessionEngine.Event.ConnectionRequestAuthorized)
                    }
                } else if (
                    engineState.connectionInfoSent &&
                    announcement.busyClientName == config.clientName
                ) {
                    // kappanhang treats the post-request busy 0x90 for its own client as
                    // the positive "serial and audio request success" signal. wfview
                    // independently uses the client-name field to distinguish our claim
                    // from another owner's busy announcement. Default radio data ports
                    // are adjacent to the configured control port.
                    defaultStreamEndpoints()?.let { endpoints ->
                        dispatch(Ic705RxSessionEngine.Event.StatusEndpointsReceived(endpoints))
                    }
                }
            }
            Ic705HandshakeCodec.STATUS_PACKET_SIZE -> {
                val status = Ic705HandshakeCodec.decodeStatusPacket(data, control.localId)
                if (
                    status.isAuthenticated &&
                    status.isConnected &&
                    status.civPort != 0 &&
                    status.audioPort != 0
                ) {
                    dispatch(
                        Ic705RxSessionEngine.Event.StatusEndpointsReceived(
                            Ic705RxSessionEngine.StreamEndpoints(status.civPort, status.audioPort),
                        ),
                    )
                } else {
                    dispatch(
                        Ic705RxSessionEngine.Event.StatusNotReady(
                            errorCode = status.errorCode,
                            disconnectFlag = status.disconnectFlag,
                        ),
                    )
                }
            }
            else -> if (isIc705VariableRetransmit(data)) handleRetransmit(control, data)
        }
    }

    private fun sendLogin() {
        val control = channelRuntimes.getValue(Ic705ChannelRole.CONTROL)
        val loginInnerSequence = if (wireProfile.loginAdvancesAuthSequence) {
            nextAuthInnerSequence()
        } else {
            authInnerSequence
        }
        sendTracked(
            control.role,
            Ic705HandshakeCodec.encodeLoginRequest(
                sequence = 0,
                senderId = control.localId,
                receiverId = requireNotNull(control.remoteId),
                innerSequence = loginInnerSequence,
                tokenRequest = localToken,
                token = radioToken,
                username = config.username,
                password = config.passwordValue(),
                clientName = config.clientName,
            ),
        )
    }

    private fun sendTokenConfirmation(token: Int) {
        val control = channelRuntimes.getValue(Ic705ChannelRole.CONTROL)
        radioToken = token
        sendTracked(
            control.role,
            Ic705HandshakeCodec.encodeTokenConfirm(
                sequence = 0,
                senderId = control.localId,
                receiverId = requireNotNull(control.remoteId),
                innerSequence = nextAuthInnerSequence(),
                tokenRequest = localToken,
                token = radioToken,
            ),
        )
        startFixedTask(
            key = TASK_TOKEN_RENEWAL,
            initialDelayMillis = config.timing.tokenRenewalMillis,
            periodMillis = config.timing.tokenRenewalMillis,
            expectedGeneration = generation,
            block = ::sendTokenRenewal,
        )
    }

    private fun sendTokenRenewal() {
        val control = channelRuntimes[Ic705ChannelRole.CONTROL] ?: return
        val remoteId = control.remoteId ?: return
        sendTracked(
            control.role,
            Ic705HandshakeCodec.encodeTokenRenewal(
                sequence = 0,
                senderId = control.localId,
                receiverId = remoteId,
                innerSequence = nextAuthInnerSequence(),
                tokenRequest = localToken,
                token = radioToken,
            ),
        )
    }

    private fun sendConnectionInfo() {
        val control = channelRuntimes.getValue(Ic705ChannelRole.CONTROL)
        val announcement = requireNotNull(connectionAnnouncement)
        sendTracked(
            control.role,
            Ic705ConnectionInfoCodec.encodeParameters(
                Ic705ConnectionParameters(
                    sequence = 0,
                    senderId = control.localId,
                    receiverId = requireNotNull(control.remoteId),
                    innerSequence = nextAuthInnerSequence(),
                    tokenRequest = localToken,
                    token = radioToken,
                    radioIdentityBlock = announcement.radioIdentityBlock,
                    radioName = announcement.radioName,
                    username = config.username,
                    localCivPort = channels[Ic705ChannelRole.CIV]?.localPort ?: (config.controlPort + 1),
                    localAudioPort = channels[Ic705ChannelRole.AUDIO]?.localPort ?: (config.controlPort + 2),
                    receiveEnabled = true,
                    // The IC-705 expects the full-duplex LPCM capability bit during LAN
                    // negotiation even for a receive-only client. This only advertises
                    // wire compatibility; this RX session exposes no TX audio or PTT API.
                    transmitEnabled = true,
                ),
            ),
        )
    }

    private fun openStreams(endpoints: Ic705RxSessionEngine.StreamEndpoints) {
        val radioAddress = discoveredRadioAddress ?: config.radioAddress
        channels[Ic705ChannelRole.CIV]?.setRemoteEndpoint(
            InetSocketAddress(radioAddress, endpoints.civPort),
            lockSource = false,
        )
        channels[Ic705ChannelRole.AUDIO]?.setRemoteEndpoint(
            InetSocketAddress(radioAddress, endpoints.audioPort),
            lockSource = false,
        )
        startDiscovery(Ic705ChannelRole.CIV)
        startDiscovery(Ic705ChannelRole.AUDIO)
    }

    private fun defaultStreamEndpoints(): Ic705RxSessionEngine.StreamEndpoints? {
        if (config.controlPort > 0xffff - 2) return null
        return Ic705RxSessionEngine.StreamEndpoints(
            civPort = config.controlPort + 1,
            audioPort = config.controlPort + 2,
        )
    }

    private fun startDiscovery(role: Ic705ChannelRole) {
        val currentGeneration = generation
        sendAreYouThere(role)
        startFixedTask(
            key = discoveryTask(role),
            initialDelayMillis = config.timing.discoveryPeriodMillis,
            periodMillis = config.timing.discoveryPeriodMillis,
            expectedGeneration = currentGeneration,
        ) { sendAreYouThere(role) }
        replaceTask(
            discoveryTimeoutTask(role),
            controlExecutor.schedule(
                {
                    if (generation == currentGeneration && channelRuntimes[role]?.remoteId == null) {
                        dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("$role discovery timeout"))
                    }
                },
                config.timing.discoveryTimeoutMillis,
                TimeUnit.MILLISECONDS,
            ),
        )
    }

    private fun sendAreYouThere(role: Ic705ChannelRole) {
        val runtime = channelRuntimes[role] ?: return
        sendUntracked(
            role,
            Ic705ControlPacketCodec.encode(
                Ic705ControlPacket(
                    type = Ic705ControlPacketCodec.TYPE_ARE_YOU_THERE,
                    sequence = 0,
                    senderId = runtime.localId,
                    receiverId = 0,
                ),
            ),
        )
    }

    private fun startPing(role: Ic705ChannelRole) {
        val currentGeneration = generation
        sendPing(role)
        startFixedTask(
            key = pingTask(role),
            initialDelayMillis = config.timing.pingPeriodMillis,
            periodMillis = config.timing.pingPeriodMillis,
            expectedGeneration = currentGeneration,
        ) { sendPing(role) }
    }

    private fun sendPing(role: Ic705ChannelRole) {
        val runtime = channelRuntimes[role] ?: return
        val remoteId = runtime.remoteId ?: return
        sendUntracked(
            role,
            Ic705HandshakeCodec.encodePing(
                Ic705PingPacket(
                    sequence = runtime.pingSequence,
                    senderId = runtime.localId,
                    receiverId = remoteId,
                    isReply = false,
                    timestampBits = System.currentTimeMillis().toInt(),
                ),
            ),
        )
    }

    private fun startIdle(role: Ic705ChannelRole) {
        startFixedTask(
            key = idleTask(role),
            initialDelayMillis = config.timing.idleCheckPeriodMillis,
            periodMillis = config.timing.idleCheckPeriodMillis,
            expectedGeneration = generation,
        ) {
            val runtime = channelRuntimes[role] ?: return@startFixedTask
            if (
                shouldSendIc705TrackedIdle(
                    runtime.trackedPackets.millisSinceLastTracked(),
                    config.timing.idleAfterMillis,
                )
            ) {
                sendTracked(
                    role,
                    controlPacket(
                        type = Ic705ControlPacketCodec.TYPE_NULL,
                        sequence = 0,
                        runtime = runtime,
                    ),
                )
            }
        }
    }

    private fun handleRetransmit(runtime: ChannelRuntime, data: ByteArray) {
        Ic705ControlPacketCodec.decodeRetransmitRequest(data, runtime.localId).forEach { sequence ->
            val stored = runtime.trackedPackets.find(sequence)
            if (stored != null) {
                sendUntracked(runtime.role, stored)
            } else if (wireProfile.replyToUnknownRetransmit) {
                sendUntracked(
                    runtime.role,
                    controlPacket(
                        type = Ic705ControlPacketCodec.TYPE_NULL,
                        sequence = sequence,
                        runtime = runtime,
                    ),
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun sendTracked(role: Ic705ChannelRole, template: ByteArray) {
        val runtime = channelRuntimes[role]
            ?: throw IOException("$role runtime is unavailable")
        val channel = channels[role]
            ?: throw IOException("$role UDP channel is unavailable")
        if (!channel.isOpen) {
            throw IOException("$role UDP channel is not open")
        }
        val tracked = runtime.trackedPackets.track(template)
        try {
            channel.send(tracked.data)
        } catch (error: Exception) {
            runtime.trackedPackets.discard(tracked.sequence)
            if (error is IOException) throw error
            throw IOException("$role UDP send failed", error)
        }
    }

    private fun sendUntracked(role: Ic705ChannelRole, data: ByteArray) {
        val channel = channels[role] ?: run {
            Log.w(TAG, "sendUntracked($role): channel not found")
            return
        }
        if (!channel.isOpen) {
            Log.w(TAG, "sendUntracked($role): channel is not open")
            return
        }
        try {
            channel.send(data)
        } catch (e: Exception) {
            Log.w(TAG, "sendUntracked($role) failed: ${e.message}")
        }
    }

    private fun controlPacket(
        type: Int,
        sequence: Int,
        runtime: ChannelRuntime,
    ): ByteArray = Ic705ControlPacketCodec.encode(
        Ic705ControlPacket(
            type = type,
            sequence = sequence,
            senderId = runtime.localId,
            receiverId = requireNotNull(runtime.remoteId),
        ),
    )

    private fun enqueueAudio(
        expectedGeneration: Long,
        runtime: ChannelRuntime,
        data: ByteArray,
    ) {
        try {
            audioExecutor.execute {
                try {
                    val result = synchronized(audioLifecycleLock) {
                        if (generation != expectedGeneration || closed.get()) {
                            return@synchronized null
                        }
                        val receiver = audioReceiver ?: return@synchronized null
                        receiver.accept(data).also { receiveResult ->
                            if (receiveResult == Ic705AudioReceiveResult.ACCEPTED) {
                                audioWrittenInGeneration = true
                            }
                        }
                    } ?: return@execute
                    runtime.lastReceivedAtMillis.set(monotonicMillis())
                    if (result == Ic705AudioReceiveResult.ACCEPTED && !firstAudioReported) {
                        submitForGeneration(expectedGeneration) {
                            if (generation == expectedGeneration && !firstAudioReported) {
                                firstAudioReported = true
                                dispatch(Ic705RxSessionEngine.Event.FirstAudio)
                            }
                        }
                    }
                } catch (_: IllegalArgumentException) {
                    submitForGeneration(expectedGeneration) {
                        reportIssue(
                            Ic705RxSessionIssueCode.MALFORMED_PACKET,
                            Ic705ChannelRole.AUDIO,
                            ic705PacketDiagnostic(data, runtime.localId),
                        )
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            audioExecutor.queue.clear()
            submitForGeneration(expectedGeneration) {
                reportIssue(Ic705RxSessionIssueCode.AUDIO_QUEUE_OVERFLOW, Ic705ChannelRole.AUDIO)
                notifyAudioReset(Ic705AudioResetReason.AUDIO_QUEUE_OVERFLOW)
            }
        }
    }

    private fun checkWatchdog() {
        val now = monotonicMillis()
        val pttPossiblyAsserted = pttStateMachine.isTransmitting
        for (role in WATCHDOG_ROLE_ORDER) {
            val runtime = channelRuntimes[role] ?: continue
            if (runtime.remoteId == null) continue
            if (
                role == Ic705ChannelRole.AUDIO &&
                shouldSuppressIc705AudioWatchdog(
                    pttPossiblyAsserted = pttPossiblyAsserted,
                    nowMillis = now,
                    graceUntilMillis = audioWatchdogGraceUntilMillis,
                )
            ) {
                continue
            }

            val lastRx = runtime.lastReceivedAtMillis.get()
            val age = (now - lastRx).coerceAtLeast(0L)
            val timeout = ic705ChannelWatchdogTimeoutMillis(config.timing, role)
            val recovery = streamRecoveries[role]

            if (recovery != null && lastRx > recovery.baselineRxMillis) {
                streamRecoveries.remove(role)
                cancelTask(discoveryTask(role))
                cancelTask(discoveryTimeoutTask(role))
                startPing(role)
                if (role == Ic705ChannelRole.CIV && wireProfile.sendTrackedIdle) startIdle(role)
                Log.i(TAG, "soft recovery succeeded role=$role attempt=${recovery.attempt} age=${age}ms")
                safeCallback {
                    callbacks.onStreamRecovery(
                        Ic705StreamRecoveryEvent(
                            role = role,
                            outcome = Ic705StreamRecoveryOutcome.SUCCEEDED,
                            attempt = recovery.attempt,
                            ageMillis = age,
                        ),
                    )
                }
                continue
            }

            val decision = ic705WatchdogDecision(
                role = role,
                ageMillis = age,
                timeoutMillis = timeout,
                pttPossiblyAsserted = pttPossiblyAsserted,
                activeRecoveryAttempt = recovery?.attempt,
                recoveryDeadlineReached = recovery?.let { now >= it.deadlineMillis } ?: false,
                maxSoftRecoveryAttempts = config.timing.streamRecoveryAttempts,
            )
            when (decision) {
                Ic705WatchdogDecision.HEALTHY,
                Ic705WatchdogDecision.WAIT_FOR_SOFT_RECOVERY -> continue

                Ic705WatchdogDecision.START_SOFT_RECOVERY,
                Ic705WatchdogDecision.RETRY_SOFT_RECOVERY -> {
                    val attempt = (recovery?.attempt ?: 0) + 1
                    beginStreamSoftRecovery(runtime, now, age, attempt)
                    return
                }

                Ic705WatchdogDecision.ESCALATE -> {
                    val attempt = recovery?.attempt ?: 0
                    streamRecoveries.remove(role)
                    Log.w(
                        TAG,
                        "watchdog escalation role=$role age=${age}ms timeout=${timeout}ms " +
                            "softAttempts=$attempt ptt=${pttStateMachine.state} " +
                            "phase=${engineState.phase} generation=$generation",
                    )
                    if (role != Ic705ChannelRole.CONTROL) {
                        safeCallback {
                            callbacks.onStreamRecovery(
                                Ic705StreamRecoveryEvent(
                                    role = role,
                                    outcome = Ic705StreamRecoveryOutcome.ESCALATED,
                                    attempt = attempt,
                                    ageMillis = age,
                                ),
                            )
                        }
                    }
                    dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("$role timeout"))
                    return
                }
            }
        }
    }

    private fun beginStreamSoftRecovery(
        runtime: ChannelRuntime,
        now: Long,
        age: Long,
        attempt: Int,
    ) {
        val role = runtime.role
        require(role != Ic705ChannelRole.CONTROL)
        streamRecoveries[role] = StreamRecoveryState(
            attempt = attempt,
            baselineRxMillis = runtime.lastReceivedAtMillis.get(),
            deadlineMillis = now + config.timing.streamRecoveryResponseMillis,
        )
        cancelTask(pingTask(role))
        cancelTask(idleTask(role))
        cancelTask(discoveryTask(role))
        cancelTask(discoveryTimeoutTask(role))

        if (role == Ic705ChannelRole.AUDIO) {
            notifyAudioReset(Ic705AudioResetReason.STREAM_RECOVERY)
            synchronized(audioLifecycleLock) {
                audioExecutor.queue.clear()
                audioReceiver = null
                audioWrittenInGeneration = false
            }
        }

        Log.w(
            TAG,
            "soft recovery start role=$role attempt=$attempt age=${age}ms " +
                "phase=${engineState.phase} generation=$generation",
        )
        safeCallback {
            callbacks.onStreamRecovery(
                Ic705StreamRecoveryEvent(
                    role = role,
                    outcome = Ic705StreamRecoveryOutcome.STARTED,
                    attempt = attempt,
                    ageMillis = age,
                ),
            )
        }

        val expectedGeneration = generation
        sendAreYouThere(role)
        startFixedTask(
            key = discoveryTask(role),
            initialDelayMillis = config.timing.discoveryPeriodMillis,
            periodMillis = config.timing.discoveryPeriodMillis,
            expectedGeneration = expectedGeneration,
        ) { sendAreYouThere(role) }
    }

    private fun scheduleConnectionInfoSettle() =
        scheduleConnectionInfoTimer(Ic705ConnectionInfoTimer.SETTLE)

    private fun scheduleConnectionInfoRetry() =
        scheduleConnectionInfoTimer(Ic705ConnectionInfoTimer.RETRY)

    private fun scheduleConnectionInfoTimer(timer: Ic705ConnectionInfoTimer) {
        cancelTask(timer.conflictingTaskKey)
        val expectedGeneration = generation
        replaceTask(
            timer.taskKey,
            controlExecutor.schedule(
                {
                    if (generation == expectedGeneration && !closed.get()) {
                        dispatch(ic705ConnectionInfoTimerEvent(timer))
                    }
                },
                ic705ConnectionInfoTimerDelayMillis(config.timing, timer),
                TimeUnit.MILLISECONDS,
            ),
        )
    }

    private fun cancelConnectionInfoTimers() {
        cancelTask(Ic705ConnectionInfoTimer.SETTLE.taskKey)
        cancelTask(Ic705ConnectionInfoTimer.RETRY.taskKey)
    }

    private fun scheduleRetry(
        attempt: Int,
        cooldown: Ic705RxSessionEngine.RetryCooldown,
    ) {
        if (!config.autoReconnect) {
            dispatch(Ic705RxSessionEngine.Event.RetryDisabled)
            return
        }
        val delay = ic705ReconnectDelayMillis(config.timing, attempt, cooldown)
        val expectedGeneration = generation
        replaceTask(
            TASK_RETRY,
            controlExecutor.schedule(
                {
                    if (generation == expectedGeneration && !closed.get()) {
                        dispatch(Ic705RxSessionEngine.Event.RetryTimerFired)
                    }
                },
                delay,
                TimeUnit.MILLISECONDS,
            ),
        )
    }

    private fun updateStageTimeout(state: Ic705RxSessionEngine.State) {
        val timeoutMillis = ic705HandshakeTimeoutMillis(config.timing, state.phase)
        if (timeoutMillis == null) {
            cancelTask(TASK_STAGE_TIMEOUT)
            return
        }
        val expectedGeneration = generation
        replaceTask(
            TASK_STAGE_TIMEOUT,
            controlExecutor.schedule(
                {
                    if (
                        generation == expectedGeneration &&
                        !closed.get() &&
                        engineState == state
                    ) {
                        dispatch(
                            Ic705RxSessionEngine.Event.RecoverableFailure(
                                "${state.phase} handshake timeout",
                            ),
                        )
                    }
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS,
            ),
        )
    }

    private fun startFixedTask(
        key: String,
        initialDelayMillis: Long,
        periodMillis: Long,
        expectedGeneration: Long,
        block: () -> Unit,
    ) {
        replaceTask(
            key,
            controlExecutor.scheduleWithFixedDelay(
                {
                    if (generation == expectedGeneration && !closed.get()) {
                        try {
                            block()
                        } catch (_: IOException) {
                            reportIssue(Ic705RxSessionIssueCode.SOCKET_IO, null)
                            dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("scheduled I/O"))
                        } catch (_: RuntimeException) {
                            reportIssue(Ic705RxSessionIssueCode.MALFORMED_PACKET, null)
                            dispatch(Ic705RxSessionEngine.Event.RecoverableFailure("scheduled task failed"))
                        }
                    }
                },
                initialDelayMillis,
                periodMillis,
                TimeUnit.MILLISECONDS,
            ),
        )
    }

    private fun replaceTask(key: String, task: ScheduledFuture<*>) {
        scheduledTasks.replace(key, task)
    }

    private fun cancelTask(key: String) {
        scheduledTasks.cancel(key)
    }

    private fun cancelAllTasks() {
        scheduledTasks.cancelAll()
    }

    private fun submitForGeneration(expectedGeneration: Long, block: () -> Unit) {
        if (closed.get()) return
        try {
            controlExecutor.execute {
                if (generation == expectedGeneration && !closed.get()) block()
            }
        } catch (_: RejectedExecutionException) {
            // Normal close race: a receive callback can arrive after executor shutdown.
        }
    }

    private fun reportIssue(
        code: Ic705RxSessionIssueCode,
        role: Ic705ChannelRole?,
        packet: Ic705PacketDiagnostic? = null,
    ) {
        safeCallback { callbacks.onIssue(Ic705RxSessionIssue(code, role, packet)) }
    }

    private fun notifyAudioReset(reason: Ic705AudioResetReason) {
        synchronized(audioLifecycleLock) {
            audioReceiver?.reset()
        }
        safeCallback { callbacks.onAudioReset(Ic705AudioReset(reason)) }
    }

    private fun safeCallback(block: () -> Unit) {
        runCatching(block)
    }

    private fun nextAuthInnerSequence(): Int {
        val result = authInnerSequence
        authInnerSequence = (authInnerSequence + 1) and 0xffff
        return result
    }

    private fun nextCivSequence(runtime: ChannelRuntime): Int {
        val result = runtime.civSequence
        runtime.civSequence = (runtime.civSequence + 1) and 0xffff
        return result
    }

    private fun discoveryTask(role: Ic705ChannelRole) = "discovery-$role"
    private fun discoveryTimeoutTask(role: Ic705ChannelRole) = "discovery-timeout-$role"
    private fun pingTask(role: Ic705ChannelRole) = "ping-$role"
    private fun idleTask(role: Ic705ChannelRole) = "idle-$role"

    private companion object {
        const val TAG = "Ic705RxSession"
        const val CLOSE_WAIT_SLICE_MILLIS = 2_000L
        const val TASK_WATCHDOG = "watchdog"
        const val TASK_TOKEN_RENEWAL = "token-renewal"
        const val TASK_RETRY = "retry"
        const val TASK_STAGE_TIMEOUT = "stage-timeout"
        const val TX_DRAIN_WAIT_MILLIS = 150L
        const val TX_COOLDOWN_GUARD_MILLIS = 300L

        val WATCHDOG_ROLE_ORDER = arrayOf(
            Ic705ChannelRole.CONTROL,
            Ic705ChannelRole.CIV,
            Ic705ChannelRole.AUDIO,
        )
    }
}

private fun namedDaemonThreadFactory(name: String): ThreadFactory = ThreadFactory { runnable ->
    Thread(runnable, name).apply { isDaemon = true }
}

private fun newAudioExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(64),
    namedDaemonThreadFactory("IC-705 RX audio"),
    ThreadPoolExecutor.AbortPolicy(),
)
