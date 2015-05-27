package iaas.uni.stuttgart.de.sitme.model;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class Subscription {

	private String situationId;
	private String objectId;

	public Subscription(String situationId, String objectId) {
		this.situationId = situationId;
		this.objectId = objectId;
	}

	public String getSituationId() {
		return situationId;
	}

	public String getObjectId() {
		return objectId;
	}
}
