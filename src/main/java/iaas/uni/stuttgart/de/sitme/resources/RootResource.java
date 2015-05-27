package iaas.uni.stuttgart.de.sitme.resources;

import iaas.uni.stuttgart.de.sitme.data.RunningTasks;
import iaas.uni.stuttgart.de.sitme.logic.TaskWorker;
import iaas.uni.stuttgart.de.sitme.model.TaskState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.mvc.Viewable;

/**
 * 
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
@Path("/")
public class RootResource {

	@GET
	@Produces(MediaType.TEXT_HTML)
	public Response root() {
		return Response.ok(new Viewable("index")).build();
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail)
			throws IOException {

		// create temp dir for receiving sent file
		java.nio.file.Path downloadTempDir = Files.createTempDirectory("sitme");
		java.nio.file.Path downloadedFile = Paths.get(
				downloadTempDir.toString(), fileDetail.getFileName());
		FileUtils.copyInputStreamToFile(uploadedInputStream,
				downloadedFile.toFile());
		
		
		// TODO create task resource which updates
		TaskState newTask = new TaskState(downloadedFile);
		
		RunningTasks.getInstance().tasks.add(newTask);
		
		new Thread(new TaskWorker(newTask)).start();


		return Response.status(200).entity("upload successful").build();

	}
}
