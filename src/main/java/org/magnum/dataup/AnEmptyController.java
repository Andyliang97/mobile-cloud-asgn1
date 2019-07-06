/*
 * 
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.magnum.dataup;

import com.google.gson.Gson;

import retrofit.http.Part;

import retrofit.mime.TypedFile;

import org.apache.commons.io.FileUtils;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;

@Controller
public class AnEmptyController{

	private List<Video> videoList = new ArrayList<Video>();
	private static final AtomicLong currentId = new AtomicLong(0L);
	private Map<Long, Video> videos = new HashMap<Long, Video>();
	private static final Gson gson = new Gson();
	private final int NOT_FOUND = 404;
	private VideoFileManager fm = VideoFileManager.get();

	@RequestMapping("/")
	public void index(HttpServletResponse response) {
		response.setContentType("text/plain");
		response.setStatus(200);
		try {
			PrintWriter sendToClient = response.getWriter();
			sendToClient.write("Welcome to My Video Control System");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@RequestMapping(value = "/video", method = RequestMethod.GET)
	@ResponseBody
	public List<Video> getVideoList() {
		return videoList;
	}

	@RequestMapping(value = "/video", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<?> createVideoInfo(@RequestBody Video video) {
		if (video.getTitle() == null) {
			// throw new error will result in 500, server error
			// throw new IllegalArgumentException("{\"error\":\"At least one parameter is
			// invalid or not supplied\"}");
			return new ResponseEntity<>(gson.toJson("Title cannot be empty"), HttpStatus.BAD_REQUEST);
		}
		checkAndSetId(video);
		save(video);
		video.setDataUrl(this.getDataUrl(video.getId()));
		//video.setLocation(this.getDataUrl(video.getId()));
		videoList.add(video);
		return new ResponseEntity<>(video, HttpStatus.ACCEPTED);
	}

	@RequestMapping(value = "/video/{id}/data", method = RequestMethod.GET)
	public void getVideo(@PathVariable("id") long id, HttpServletResponse response){
		if (videos.containsKey(id)) {
			Path targetDir_ = Paths.get("videos");
			Path file = targetDir_.resolve("video"+Long.toString(id)+".mpg");
	        if (Files.exists(file))
	        {
	            response.setContentType("video/mpeg");
	            try
	            {
	                Files.copy(file, response.getOutputStream());
	                response.getOutputStream().flush();
	            }
	            catch (IOException ex) {
	                ex.printStackTrace();
	            }
	        }
	        else {
	        	try {
					response.sendError(404);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
		}else {
			try {
				response.sendError(NOT_FOUND, "Resourse not Found");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		/*
		try {
			return videos.get(Long.parseLong(id));
		}
		catch (NumberFormatException exception) {
            // Output expected NumberFormatException.
            System.out.println(exception);
        } catch (Exception exception) {
            // Output unexpected Exceptions.
        	System.out.println(exception);
        }
		return null;
		*/
	}
	
	
	/**
	 * The working route
	 * It allows users to upload the file to our file system.
	 * @param id the video id
	 * @param videoData the video
	 * @param request request
	 * @return file upload status
	 */
	@RequestMapping(value = "/video/{id}/data", method = RequestMethod.POST)
	@ResponseBody
	public VideoStatus setVideo(@PathVariable("id") long id, @RequestParam("data") MultipartFile videoData, HttpServletRequest request) {
		if (!videoData.isEmpty() && videos.containsKey(id)) {
			try {
				fm.saveVideoData(videos.get(id), videoData.getInputStream());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return new VideoStatus(VideoStatus.VideoState.READY);
		}
		else {
			throw new IllegalArgumentException("The argument is invalid");
		}
		
	}
	
	/**
	 * Something is wrong with this route. I will just leave it here and ask for help someday.
	 * @param id the id in the link
	 * @param videoData the actual video
	 * @param request the request 
	 * @param response the response
	 */
	@RequestMapping(value = "/videos/{id}/postnotsupport", method = RequestMethod.POST)
	public @ResponseBody void setVideoData(@PathVariable("id") long id, @RequestParam("data") MultipartFile videoData, 
			HttpServletRequest request, HttpServletResponse response) {
		if (!videoData.isEmpty()) {
			if (videos.containsKey(id)) {
				try {
					/* Way 1:
					String filePath = System. getProperty("user.dir");
					Files.write(Paths.get(filePath, videoData.getOriginalFilename()), videoData.getBytes());
					*/
					/* Way 2
					String filePath = request.getSession().getServletContext().getRealPath("/") + "upload/"  + videoData.getOriginalFilename();  
					File newFile = new File(filePath);
					FileUtils.copyInputStreamToFile(videoData.getInputStream(), newFile);
					*/
					/* Way 3 */
					fm.saveVideoData(videos.get(id), videoData.getInputStream());
					VideoStatus status = new VideoStatus(VideoStatus.VideoState.READY);
					//return null;
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					//return null;
				}
			}
			else {
				throw new IllegalArgumentException("The argument is invalid");
			}
		}
		else {
			try {
				response.sendError(NOT_FOUND, "file is empty");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//return null;
		}
	}


	public Video save(Video entity) {
		checkAndSetId(entity);
		videos.put(entity.getId(), entity);
		return entity;
	}

	private void checkAndSetId(Video entity) {
		if (entity.getId() == 0) {
			entity.setId(currentId.incrementAndGet());
		}
	}

	private String getDataUrl(long videoId) {
		String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
		return url;
	}

	private String getUrlBaseForLocalServer() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		String base = "http://" + request.getServerName()
				+ ((request.getServerPort() != 80) ? ":" + request.getServerPort() : "");
		return base;
	}

	//This exception handler is used to handle  Long.parseLong. Primarily for Get /video/{id}/data
	@ExceptionHandler({IllegalArgumentException.class, NumberFormatException.class})
	void handleBadRequests(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.NOT_FOUND.value(), "Invalid argument or url");
	}
	
	@ExceptionHandler(HttpMessageNotReadableException.class) 
	public void handleBadInput(HttpMessageNotReadableException ex) 
	{ 
		Throwable cause = ex.getCause();
	}
	

}
