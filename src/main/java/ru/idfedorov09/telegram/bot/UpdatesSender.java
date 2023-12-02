package ru.idfedorov09.telegram.bot;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.net.ConnectException;

public class UpdatesSender {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    protected Response sendUpdate(Update update, String botWebhookUrl) {
        Gson gson = new Gson();
        String jsonUpdate = gson.toJson(update);

        OkHttpClient httpClient = new OkHttpClient();
        RequestBody requestBody = RequestBody.create(
                jsonUpdate,
                okhttp3.MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(botWebhookUrl)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response;
        } catch (ConnectException e) {
            log.warn(botWebhookUrl+" is offline.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    protected void exceptHandle(IOException e){
        e.printStackTrace();
    }

}
