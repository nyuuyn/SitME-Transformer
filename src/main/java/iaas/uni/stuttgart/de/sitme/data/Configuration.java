package iaas.uni.stuttgart.de.sitme.data;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class Configuration {
	
	private String wso2BpsAddress = "https://localhost:9443";
	private String wso2BpsUserLogin = "admin";
	private String wso2BpsPwLogin = "admin";
	
	private String srsServiceAddress = "http://localhost:8080/srsTestService/services/srsService";
	// portName = srsCallbackServiceSOAP serviceName = srsService, endpoint pattern for wso2bps 3.2.0 localhost:9763/services/{serviceName}.{serviceName}{http}{portName}{Endpoint}
	private String srsServiceCallbackAddress = "http://localhost:9763/services/srsServiceCallback.srsServiceCallbackhttpsrsCallbackServiceSOAPEndpoint/";
	

	public Configuration() {
	}
	
	public String getWso2BpsAddress() {
		return wso2BpsAddress;
	}

	public void setWso2BpsAddress(String wso2BpsAddress) {
		this.wso2BpsAddress = wso2BpsAddress;
	}

	public String getWso2BpsUserLogin() {
		return wso2BpsUserLogin;
	}

	public void setWso2BpsUserLogin(String wso2BpsUserLogin) {
		this.wso2BpsUserLogin = wso2BpsUserLogin;
	}

	public String getWso2BpsPwLogin() {
		return wso2BpsPwLogin;
	}

	public void setWso2BpsPwLogin(String wso2BpsPwLogin) {
		this.wso2BpsPwLogin = wso2BpsPwLogin;
	}
	
	public String getSrsServiceAddress(){
		return this.srsServiceAddress;
	}
	
	public void setSrsServiceAddress(String srsServiceAddress){
		this.srsServiceAddress = srsServiceAddress;
	}

	public String getSrsServiceCallbackAddress() {
		return srsServiceCallbackAddress;
	}
	
	public void setSrsServiceCallbackAddress(String srsServiceCallbackAddress) {
		this.srsServiceCallbackAddress = srsServiceCallbackAddress;
	}
}
