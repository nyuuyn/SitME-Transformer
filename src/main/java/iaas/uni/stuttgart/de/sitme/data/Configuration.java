package iaas.uni.stuttgart.de.sitme.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class Configuration {
	
	private String wso2BpsUserLogin = "admin";
	private String wso2BpsPwLogin = "admin";
	
	private String srsServiceAddress = "http://localhost:8080/srsTestService/services/srsService";
	// portName = srsCallbackServiceSOAP serviceName = srsService, endpoint pattern for wso2bps 3.2.0 localhost:9763/services/{serviceName}.{serviceName}{http}{portName}{Endpoint}
	private String srsServiceCallbackAddress = "http://localhost:9763/services/srsServiceCallback.srsServiceCallbackhttpsrsCallbackServiceSOAPEndpoint/";
	

	
	private Properties getProperties() {
		Properties prop = new Properties();
		InputStream inStream = this.getClass().getClassLoader().getResourceAsStream("/config.props");
		try {
			prop.load(inStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return prop;
	}
	
	public String getWso2BpsAddress() {
		return this.getProperties().getProperty("wso2ServiceUrl");
	}


	public String getWso2BpsUserLogin() {
		return wso2BpsUserLogin;
	}

	

	public String getWso2BpsPwLogin() {
		return wso2BpsPwLogin;
	}
	
	public String getSrsServiceAddress(){
		return this.getProperties().getProperty("srsServiceUrl");
	}
	

	public String getSrsServiceCallbackAddress() {
		return srsServiceCallbackAddress;
	}
	
}
