package it.eng.idsa.dataapp.service.impl;


import static de.fraunhofer.iais.eis.util.Util.asList;

import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import de.fraunhofer.iais.eis.*;
import org.apache.maven.model.Model;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import it.eng.idsa.dataapp.service.MultiPartMessageService;
import it.eng.idsa.multipart.domain.MultipartMessage;
import it.eng.idsa.multipart.processor.MultipartMessageProcessor;
import it.eng.idsa.multipart.util.DateUtil;

/**
 *
 * @author Milan Karajovic and Gabriele De Luca
 *
 */


/**
 * Service Implementation for managing MultiPartMessage.
 */
@Service
@Transactional
public class MultiPartMessageServiceImpl implements MultiPartMessageService {

	private final static String informationModelVersion = getInformationModelVersion();

	@Override
	public String getHeader(String body) {
		MultipartMessage deserializedMultipartMessage = MultipartMessageProcessor.parseMultipartMessage(body);
		return deserializedMultipartMessage.getHeaderContentString();
	}

	@Override
	public String getPayload(String body) {
		MultipartMessage deserializedMultipartMessage = MultipartMessageProcessor.parseMultipartMessage(body);
		return deserializedMultipartMessage.getPayloadContent();
	}

	@Override
	public Message getMessage(String body) {
		MultipartMessage deserializedMultipartMessage = MultipartMessageProcessor.parseMultipartMessage(body);
		return deserializedMultipartMessage.getHeaderContent();
	}

	@Override
	public String addToken(Message message, String token) {
		String output = null;
		try {
			String msgSerialized = new Serializer().serializePlainJson(message);
			Token tokenJsonValue = new TokenBuilder()
					._tokenFormat_(TokenFormat.JWT)
					._tokenValue_(token).build();
			String tokenValueSerialized=new Serializer().serializePlainJson(tokenJsonValue);
			JSONParser parser = new JSONParser();
			JSONObject jsonObject = (JSONObject) parser.parse(msgSerialized);
			JSONObject jsonObjectToken = (JSONObject) parser.parse(tokenValueSerialized);
			jsonObject.put("authorizationToken",jsonObjectToken);
			output=new Serializer().serializePlainJson(jsonObject);
		} catch (JsonProcessingException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return output;
	}

	@Override
	public String removeToken(Message message) {
		String output = null;
		try {
			String msgSerialized = new Serializer().serializePlainJson(message);
			JSONParser parser = new JSONParser();
			JSONObject jsonObject = (JSONObject) parser.parse(msgSerialized);
			jsonObject.remove("authorizationToken");
			output=new Serializer().serializePlainJson(jsonObject);
		} catch (JsonProcessingException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return output;
	}


    @Override
    public String getResponseHeader(String header) {
	    Message message = null;
        if(null == header || header.isEmpty() || "null".equalsIgnoreCase(header)) {
            message = new MessageBuilder().build();
        } else
            message = getIDSMessage(header);
        return getResponseHeader(message);
    }

    @Override
    public String getResponseHeader(Message header) {
        String output = null;
        try {
            if(null == header || null == header.getId() || header.getId().toString().isEmpty())
                header = new MessageBuilder().build();
            if (header instanceof ArtifactRequestMessage){
                output = new Serializer().serializePlainJson(createArtifactResponseMessage(header));
            } else {
                output = new Serializer().serializePlainJson(createResultMessage(header));
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return output;
    }



    @Override
	public Message getMessage(Object header) {
		Message message = null;
		try {
			message = new Serializer().deserialize(String.valueOf(header), Message.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return message;

	}



	public Message getIDSMessage(String header) {
		Message message = null;
		try {
			message = new Serializer().deserialize(String.valueOf(header), Message.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return message;
	}

	@Override
	public HttpEntity createMultipartMessage(String header, String payload/*, String boundary, String contentType*/) {
		MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
		try {
			multipartEntityBuilder.addTextBody("header", new Serializer().serializePlainJson(createResultMessage(getIDSMessage(header))));
		} catch (JsonProcessingException e1) {
			// TODO Auto-generated catch block
			try {
				multipartEntityBuilder.addTextBody("header", new Serializer().serializePlainJson(createRejectionMessageLocalIssues(getMessage(header))));
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				multipartEntityBuilder.addTextBody("header", "INTERNAL ERROR");
				e.printStackTrace();
			}
			e1.printStackTrace();
		}
		if(payload!=null) {
			multipartEntityBuilder.addTextBody("payload", payload);
		}

		// multipartEntityBuilder.setBoundary(boundary)
		HttpEntity multipart = multipartEntityBuilder.build();

		//return multipart;
		InputStream streamHeader = new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8));
		InputStream streamPayload = null;
		if(payload!=null) {
			streamPayload = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
		}

		multipartEntityBuilder = MultipartEntityBuilder.create().setMode(HttpMultipartMode.STRICT);
		try {
			FormBodyPart bodyHeaderPart;

			/*
			 * bodyHeaderPart = FormBodyPartBuilder.create() .addField("Content-Lenght",
			 * ""+header.length()) .setName("header") .setBody(new StringBody(header))
			 * .build();
			 */
			bodyHeaderPart = new FormBodyPart("header", new StringBody( new Serializer().serializePlainJson(createResultMessage(getIDSMessage(header))), ContentType.DEFAULT_TEXT)) {
				@Override
				protected void generateContentType(ContentBody body) {
				}
				@Override
				protected void generateTransferEncoding(ContentBody body){
				}
			};
			bodyHeaderPart.addField("Content-Lenght", ""+header.length());

			FormBodyPart bodyPayloadPart=null;
			if(payload!=null) {
				bodyPayloadPart=new FormBodyPart("payload", new StringBody(payload, ContentType.DEFAULT_TEXT)) {
					@Override
					protected void generateContentType(ContentBody body) {
					}
					@Override
					protected void generateTransferEncoding(ContentBody body){
					}
				};
				bodyPayloadPart.addField("Content-Lenght", ""+payload.length());
			}

			/*
			 * = FormBodyPartBuilder.create() .addField("Content-Lenght",
			 * ""+payload.length()) .setName("payload") .setBody(new StringBody(payload))
			 * .build();
			 */


			multipartEntityBuilder.addPart (bodyHeaderPart);
			if(bodyPayloadPart!=null) {
				multipartEntityBuilder.addPart(bodyPayloadPart);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return multipartEntityBuilder.build();
	}

	private static String getInformationModelVersion() {
		String currnetInformationModelVersion = null;
		try {
			MavenXpp3Reader reader = new MavenXpp3Reader();
			Model model = reader.read(new FileReader("pom.xml"));

			for (int i = 0; i < model.getDependencies().size(); i++) {
				if (model.getDependencies().get(i).getGroupId().equalsIgnoreCase("de.fraunhofer.iais.eis.ids.infomodel")){
					String version=model.getDependencies().get(i).getVersion();
					// If we want, we can delete "-SNAPSHOT" from the version
//					if (version.contains("-SNAPSHOT")) {
//						version=version.substring(0,version.indexOf("-SNAPSHOT"));
//					}
					currnetInformationModelVersion=version;
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return currnetInformationModelVersion;
	}

	@Override
	public String getToken(String message) {
		JsonObject messageObject = new JsonParser().parse(message).getAsJsonObject();
		JsonObject authorizationTokenObject = messageObject.get("authorizationToken").getAsJsonObject();
		String token = authorizationTokenObject.get("tokenValue").getAsString();
		return token;
	}

	public Message createResultMessage(Message header) {
		return new ResultMessageBuilder()
				._issuerConnector_(whoIAm())
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				._recipientConnector_(asList(header.getIssuerConnector()))
				._correlationMessage_(header.getId())
				.build();
	}

	public Message createArtifactResponseMessage(Message header) {
		return new ArtifactResponseMessageBuilder()
				._issuerConnector_(whoIAm())
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				._recipientConnector_(asList(header.getIssuerConnector()))
				._correlationMessage_(header.getId())
				.build();
	}


	public Message createRejectionMessage(Message header) {
		return new RejectionMessageBuilder()
				._issuerConnector_(whoIAm())
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				._recipientConnector_(header!=null?asList(header.getIssuerConnector()):asList(URI.create("auto-generated")))
				._correlationMessage_(header!=null?header.getId():URI.create(""))
				._rejectionReason_(RejectionReason.MALFORMED_MESSAGE)
				.build();
	}

	public Message createRejectionToken(Message header) {
		return new RejectionMessageBuilder()
				._issuerConnector_(whoIAm())
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				._recipientConnector_(asList(header.getIssuerConnector()))
				._correlationMessage_(header.getId())
				._rejectionReason_(RejectionReason.NOT_AUTHENTICATED)
				.build();
	}


	private URI whoIAm() {
		//TODO
		return URI.create("auto-generated");
	}

	public Message createRejectionMessageLocalIssues(Message header) {
		return new RejectionMessageBuilder()
				._issuerConnector_(URI.create("auto-generated"))
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				//._recipientConnectors_(header!=null?asList(header.getIssuerConnector()):asList(URI.create("auto-generated")))
				._correlationMessage_(URI.create("auto-generated"))
				._rejectionReason_(RejectionReason.MALFORMED_MESSAGE)
				.build();
	}

	public Message createRejectionTokenLocalIssues(Message header) {
		return new RejectionMessageBuilder()
				._issuerConnector_(header.getIssuerConnector())
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				._recipientConnector_(asList(header.getIssuerConnector()))
				._correlationMessage_(header.getId())
				._rejectionReason_(RejectionReason.NOT_AUTHENTICATED)
				.build();
	}


	public Message createRejectionCommunicationLocalIssues(Message header) {
		return new RejectionMessageBuilder()
				._issuerConnector_(header.getIssuerConnector())
				._issued_(DateUtil.now())
				._modelVersion_(informationModelVersion)
				._recipientConnector_(asList(header.getIssuerConnector()))
				._correlationMessage_(header.getId())
				._rejectionReason_(RejectionReason.NOT_FOUND)
				.build();
	}

}
