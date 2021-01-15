package com.acgist.snail.net.torrent.dht;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.acgist.snail.config.PeerConfig.Source;
import com.acgist.snail.context.SystemStatistics;
import com.acgist.snail.context.exception.DownloadException;
import com.acgist.snail.net.torrent.peer.PeerManager;
import com.acgist.snail.pojo.bean.InfoHash;
import com.acgist.snail.pojo.session.TorrentSession;
import com.acgist.snail.utils.Performance;

public class DhtLauncherTest extends Performance {

	@Test
	public void testDhtLauncher() throws DownloadException {
		if(SKIP) {
			this.log("跳过testDhtLauncher测试");
			return;
		}
		if(NodeManager.getInstance().nodes().isEmpty()) {
			this.log("没有系统节点");
			return;
		}
		final String infoHashHex = "261adf9754a0eece8e2a228cda4e46102ae86629";
		final DhtLauncher launcher = DhtLauncher.newInstance(TorrentSession.newInstance(InfoHash.newInstance(infoHashHex), null));
		launcher.put("127.0.0.1", 18888);
		PeerManager.getInstance().newPeerSession(infoHashHex, SystemStatistics.getInstance().statistics(), "128.0.0.1", 18888, Source.CONNECT);
		launcher.run();
		this.log(NodeManager.getInstance().nodes().size());
		assertTrue(PeerManager.getInstance().hasPeerSession(infoHashHex));
	}
	
}