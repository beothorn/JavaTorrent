package torrent.download.algos;

import org.johnnei.utils.config.Config;

import torrent.download.MetadataFile;
import torrent.download.Torrent;
import torrent.download.tracker.TrackerManager;

public class PhasePreMetadata extends AMetadataPhase {
	
	private int metadataSize;

	public PhasePreMetadata(TrackerManager trackerManager, Torrent torrent) {
		super(trackerManager, torrent);
	}

	@Override
	public boolean isDone() {
		return metadataSize != 0;
	}

	@Override
	public IDownloadPhase nextPhase() {
		return new PhaseMetadata(trackerManager, torrent);
	}

	@Override
	public void process() {
		// Wait for peers to connect with the correct information.
	}
	
	@Override
	public void preprocess() {
		super.preprocess();
		if (foundMatchingFile) {
			metadataSize = (int) Config.getConfig().getTorrentFileFor(torrent).length();
		}
	}

	@Override
	public void postprocess() {
		MetadataFile metadata = new MetadataFile(torrent, metadataSize);
		torrent.setFiles(metadata);
		torrent.setMetadata(metadata);
	}
	
	public void setMetadataSize(int metadataSize) {
		this.metadataSize = metadataSize;
	}

}
