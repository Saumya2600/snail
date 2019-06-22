package com.acgist.snail.tracker;

import org.junit.Test;

import com.acgist.snail.net.bt.TorrentManager;
import com.acgist.snail.net.bt.tracker.bootstrap.impl.HttpTrackerClient;
import com.acgist.snail.pojo.session.TorrentSession;
import com.acgist.snail.system.exception.DownloadException;
import com.acgist.snail.system.exception.NetException;

public class TrackerClientHttpTest {

	@Test
	public void test() throws DownloadException, NetException {
		String path = "e:/snail/16b1233b33143700fe47910898fcaaf0f05d2d09.torrent";
		TorrentSession session = TorrentManager.getInstance().newTorrentSession(path);
//		HttpTrackerClient client = HttpTrackerClient.newInstance("http://anidex.moe:6969/announce");
		HttpTrackerClient client = HttpTrackerClient.newInstance("http://t.nyaatracker.com/announce");
		client.announce(1000, session);
	}
	
}
