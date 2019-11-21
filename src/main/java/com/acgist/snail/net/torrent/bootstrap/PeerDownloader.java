package com.acgist.snail.net.torrent.bootstrap;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acgist.snail.net.torrent.PeerConnect;
import com.acgist.snail.net.torrent.peer.PeerClient;
import com.acgist.snail.net.torrent.peer.bootstrap.PeerEvaluator;
import com.acgist.snail.net.torrent.peer.bootstrap.PeerEvaluator.Type;
import com.acgist.snail.net.torrent.peer.bootstrap.PeerSubMessageHandler;
import com.acgist.snail.net.torrent.utp.UtpClient;
import com.acgist.snail.pojo.bean.TorrentPiece;
import com.acgist.snail.pojo.session.PeerSession;
import com.acgist.snail.pojo.session.TorrentSession;
import com.acgist.snail.system.config.PeerConfig;
import com.acgist.snail.utils.ThreadUtils;

/**
 * <p>Peer下载</p>
 * <p>提供下载功能：根据是否支持UTP选择使用UTP还是TCP</p>
 * 
 * @author acgist
 * @since 1.1.0
 */
public class PeerDownloader extends PeerConnect {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PeerDownloader.class);

	/**
	 * <p>每批请求的SLICE数量</p>
	 * <p>注：过大会导致UTP信号量阻塞</p>
	 * 
	 * TODO：根据速度优化
	 */
	private static final int SLICE_REQUEST_SIZE = 2;
	/**
	 * SLICE请求等待时间
	 */
	private static final int SLICE_AWAIT_TIME = 20;
	/**
	 * PICEC完成等待时间
	 */
	private static final int PIECE_AWAIT_TIME = 40;
	/**
	 * 释放时等待时间
	 */
	private static final int CLOSE_AWAIT_TIME = 4;
	
	/**
	 * 是否上传
	 */
	private final boolean uploading;
	/**
	 * 是否下载
	 */
	private volatile boolean downloading = false;
	/**
	 * 是否返回数据
	 */
	private volatile boolean havePieceMessage = false;
	/**
	 * 当前下载Piece信息
	 */
	private TorrentPiece downloadPiece;
	/**
	 * Peer下载评分
	 */
	private final AtomicLong downloadMark = new AtomicLong(0);
	/**
	 * Piece分片锁
	 */
	private final AtomicInteger countLock = new AtomicInteger(0);
	/**
	 * 释放锁
	 */
	private final AtomicBoolean releaseLock = new AtomicBoolean(false);
	/**
	 * Piece完成锁
	 */
	private final AtomicBoolean completeLock = new AtomicBoolean(false);
	
	private PeerDownloader(PeerSession peerSession, TorrentSession torrentSession) {
		super(peerSession, torrentSession, PeerSubMessageHandler.newInstance(peerSession, torrentSession));
		this.uploading = false;
	}
	
	protected PeerDownloader(PeerSession peerSession, TorrentSession torrentSession, PeerSubMessageHandler peerSubMessageHandler) {
		super(peerSession, torrentSession, peerSubMessageHandler);
		this.uploading = true;
	}
	
	public static final PeerDownloader newInstance(PeerSession peerSession, TorrentSession torrentSession) {
		return new PeerDownloader(peerSession, torrentSession);
	}
	
	/**
	 * <p>握手</p>
	 * <p>建立连接、发送握手</p>
	 * 
	 * TODO：去掉保留地址
	 */
	public boolean handshake() {
		final boolean ok = connect();
		if(ok) {
			PeerEvaluator.getInstance().score(this.peerSession, PeerEvaluator.Type.CONNECT);
			this.peerSubMessageHandler.handshake(this);
		} else {
			this.peerSession.fail();
		}
		this.available = ok;
		return ok;
	}
	
	/**
	 * <p>建立连接</p>
	 */
	private boolean connect() {
		if(this.peerSession.utp()) {
			LOGGER.debug("Peer连接（uTP）：{}-{}", this.peerSession.host(), this.peerSession.port());
			final UtpClient utpClient = UtpClient.newInstance(this.peerSession, this.peerSubMessageHandler);
			return utpClient.connect();
		} else {
			LOGGER.debug("Peer连接（TCP）：{}-{}", this.peerSession.host(), this.peerSession.port());
			final PeerClient peerClient = PeerClient.newInstance(this.peerSession, this.peerSubMessageHandler);
			final boolean tcpOk = peerClient.connect();
			if(tcpOk) {
				return tcpOk;
			} else {
				LOGGER.debug("Peer连接（uTP）（重试）：{}-{}", this.peerSession.host(), this.peerSession.port());
				final UtpClient utpClient = UtpClient.newInstance(this.peerSession, this.peerSubMessageHandler);
				final boolean utpOk = utpClient.connect();
				if(utpOk) {
					this.peerSession.flags(PeerConfig.PEX_UTP);
				}
				return utpOk;
			}
		}
	}

	/**
	 * <p>Peer下载评分</p>
	 * <p>获取评分后清零，下次重新开始计算。</p>
	 */
	@Override
	public long downloadMark() {
		return this.downloadMark.getAndSet(0);
	}
	
	@Override
	public void download() {
		if(!this.downloading) {
			synchronized (this) {
				if(!this.downloading) {
					this.downloading = true;
					this.torrentSession.submit(() -> {
						requests();
					});
				}
			}
		}
	}
	
	@Override
	public void piece(int index, int begin, byte[] bytes) {
		// 数据不完整抛弃当前块：重新选择下载块
		if(bytes == null) {
			undone();
			return;
		}
		if(index != this.downloadPiece.getIndex()) {
			LOGGER.warn("下载Piece索引和当前Piece索引不符：{}-{}", index, this.downloadPiece.getIndex());
			return;
		}
		this.havePieceMessage = true;
		mark(bytes.length); // 评分
		// 请求数据下载完成：唤醒下载等待
		synchronized (this.countLock) {
			if (this.countLock.addAndGet(-1) <= 0) {
				this.countLock.notifyAll();
			}
		}
		final boolean complete = this.downloadPiece.put(begin, bytes);
		// 唤醒下载完成等待
		if(complete) {
			synchronized (this.completeLock) {
				if (this.completeLock.getAndSet(true)) {
					this.completeLock.notifyAll();
				}
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 * <p>关闭Peer客户端、设置非下载状态</p>
	 */
	@Override
	public void releaseDownload() {
		try {
			if(this.available && this.downloading) {
				LOGGER.debug("PeerDownloader关闭：{}-{}", this.peerSession.host(), this.peerSession.port());
				this.downloading = false;
				if(!this.uploading) { // 上传：不能修改状态
					this.available = false;
				}
				// 没有完成：等待下载完成
				if(!this.completeLock.get()) {
					if(!this.releaseLock.get()) {
						synchronized (this.releaseLock) {
							if(!this.releaseLock.getAndSet(true)) {
								ThreadUtils.wait(this.releaseLock, Duration.ofSeconds(CLOSE_AWAIT_TIME));
							}
						}
					}
				}
				if(!this.uploading) { // 上传：不能关闭
					this.peerSubMessageHandler.close();
				}
				PeerEvaluator.getInstance().score(this.peerSession, Type.DOWNLOAD);
			}
		} catch (Exception e) {
			LOGGER.error("PeerDownloader关闭异常", e);
		} finally {
			this.peerSession.statusOff(PeerConfig.STATUS_DOWNLOAD);
			this.peerSession.peerDownloader(null);
			if(!this.uploading) { // 上传：不能重置
				this.peerSession.reset();
			}
		}
	}
	
	/**
	 * <dl>
	 * 	<dt>释放所有资源</dt>
	 * 	<dd>发送不感兴趣消息</dd>
	 * 	<dd>设置完成状态</dd>
	 * 	<dd>释放Peer下载</dd>
	 * 	<dd>完成检测</dd>
	 * </dl>
	 */
	private void release() {
		this.peerSubMessageHandler.notInterested(); // 发送不感兴趣消息
		this.completeLock.set(true);
		this.releaseDownload();
		this.torrentSession.checkCompletedAndDone(); // 完成下载检测
	}
	
	/**
	 * <p>下载失败</p>
	 */
	private void undone() {
		LOGGER.debug("Piece下载失败：{}", this.downloadPiece.getIndex());
		this.torrentSession.undone(this.downloadPiece);
	}

	/**
	 * <p>循环请求下载</p>
	 */
	private void requests() {
		LOGGER.debug("开始请求下载：{}", this.peerSession);
		boolean ok = true;
		while(ok) {
			try {
				ok = request();
			} catch (Exception e) {
				LOGGER.error("Peer请求异常", e);
				ok = false;
			}
		}
		LOGGER.debug("结束请求下载：{}", this.peerSession);
	}
	
	/**
	 * <p>请求数据</p>
	 * <p>每次发送{@link #SLICE_REQUEST_SIZE}个请求，进入等待，当全部数据响应后，又开始发送请求，直到Piece下载完成。</p>
	 * <p>请求发送完成后进入完成等待。</p>
	 * <p>每次请求如果等待时间超过{@link #SLICE_AWAIT_TIME}跳出下载。</p>
	 * <p>如果最后Piece没有下载完成标记为失败。</p>
	 * 
	 * @return 是否可以继续下载
	 */
	private boolean request() {
		if(!available()) {
			return false;
		}
		pickDownloadPiece();
		if(this.downloadPiece == null) {
			LOGGER.debug("没有匹配Piece下载：释放Peer");
			this.release();
			return false;
		}
		if(!this.torrentSession.downloadable()) {
			LOGGER.debug("任务不可下载：释放Peer");
			this.release();
			return false;
		}
		final int index = this.downloadPiece.getIndex();
		while(available()) {
			if (this.countLock.get() >= SLICE_REQUEST_SIZE) {
				synchronized (this.countLock) {
					ThreadUtils.wait(this.countLock, Duration.ofSeconds(SLICE_AWAIT_TIME));
					// 如果没有数据返回直接跳出下载
					if (!this.havePieceMessage) {
						break;
					}
				}
			}
			this.countLock.addAndGet(1);
			int begin = this.downloadPiece.position();
			int length = this.downloadPiece.length(); // 顺序不能调换
			this.peerSubMessageHandler.request(index, begin, length);
			// 是否还有更多SLICE
			if(!this.downloadPiece.hasMoreSlice()) {
				break;
			}
		}
		/*
		 * <p>此处不论是否有数据返回都需要进行结束等待，防止数据小于{@link #SLICE_REQUEST_SIZE}个slice时直接跳出了slice wait（countLock）导致响应还没有收到就直接结束了。</p>
		 * <p>设置完成锁为true，释放时不用等待。</p>
		 */
		synchronized (this.completeLock) {
			if(!this.completeLock.getAndSet(true)) {
				ThreadUtils.wait(this.completeLock, Duration.ofSeconds(PIECE_AWAIT_TIME));
			}
		}
		// 如果已经释放，唤醒释放锁。
		if(this.releaseLock.get()) {
			synchronized (this.releaseLock) {
				this.releaseLock.notifyAll();
			}
		}
		// 没有下载完成
		if(this.countLock.get() > 0) {
			undone();
		}
		return true;
	}

	/**
	 * 选择下载Piece
	 */
	private void pickDownloadPiece() {
		if(!available()) {
			return;
		}
		if(this.downloadPiece == null) { // 没有Piece
		} else if(this.downloadPiece.complete()) { // 下载完成
			// 验证数据
			if(this.downloadPiece.verify()) {
				// 保存数据
				final boolean ok = this.torrentSession.write(this.downloadPiece);
				if(ok) {
					// 统计下载数据
					this.peerSession.download(this.downloadPiece.getLength());
				}
			} else {
				this.peerSession.badPieces(this.downloadPiece.getIndex());
				LOGGER.warn("Piece校验失败：{}", this.downloadPiece.getIndex());
			}
		} else { // Piece没有下载完成
			LOGGER.debug("Piece没有下载完成：{}", this.downloadPiece.getIndex());
		}
		if(this.peerSession.isPeerUnchoked()) { // 解除阻塞
			LOGGER.debug("选择下载块：解除阻塞");
			this.downloadPiece = this.torrentSession.pick(this.peerSession.availablePieces(), this.peerSession.suggestPieces());
		} else { // 快速允许
			LOGGER.debug("选择下载块：快速允许");
			this.downloadPiece = this.torrentSession.pick(this.peerSession.allowedPieces(), this.peerSession.allowedPieces());
		}
		if(this.downloadPiece != null) {
			LOGGER.debug("选取Piece：{}-{}-{}", this.downloadPiece.getIndex(), this.downloadPiece.getBegin(), this.downloadPiece.getEnd());
		}
		this.countLock.set(0);
		this.completeLock.set(false);
		this.havePieceMessage = false;
	}

	/**
	 * 计算评分：每次下载都将下载的数据大小加入评分
	 */
	private void mark(int buffer) {
		this.downloadMark.addAndGet(buffer); // 计算评分
	}

}