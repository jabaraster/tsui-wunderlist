package jabara.tsui_wunderlist;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

/**
 * @author jabaraster
 */
@SuppressWarnings("nls")
public class TsuiTaskAllDelete {

    /** Application name. */
    private static final String         APPLICATION_NAME = TsuiTaskAllDelete.class.getName();

    /** Directory to store user credentials for this application. */
    private static final java.io.File   DATA_STORE_DIR   = new java.io.File(System.getProperty("user.home"),
            ".credentials/" + TsuiTaskAllDelete.class.getName() + ".json");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory    JSON_FACTORY     = JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport        HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials at
     * ~/.credentials/calendar-java-quickstart.json
     */
    private static final List<String>   SCOPES           = Arrays.asList(CalendarScopes.CALENDAR);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (final Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        try (final InputStream in = TsuiTaskAllDelete.class.getResourceAsStream("/client_secret.json")) {
            final GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
            final Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
            System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
            return credential;
        }
    }

    /**
     * Build and return an authorized Calendar client service.
     *
     * @return an authorized Calendar client service
     * @throws IOException
     */
    public static com.google.api.services.calendar.Calendar getCalendarService() throws IOException {
        final Credential credential = authorize();
        return new com.google.api.services.calendar.Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * @param args
     * @throws IOException
     */
    public static void main(final String[] args) throws IOException {
        final String calendarId = getEnv("CALENDAR_ID");

        // Build a new authorized API client service.
        // Note: Do not confuse this class with the
        // com.google.api.services.calendar.model.Calendar class.
        final com.google.api.services.calendar.Calendar service = getCalendarService();

        deleteTasks(service, calendarId);
        insertTasks(service, calendarId);
    }

    private static void deleteTasks(final com.google.api.services.calendar.Calendar pService, final String pCalendarId) throws IOException {
        String pageToken = null;
        do {
            final Events events = pService.events().list(pCalendarId).setPageToken(pageToken).execute();
            final List<Event> items = events.getItems();
            for (final Event event : items) {
                System.out.println(event.getSummary());
                pService.events().delete(pCalendarId, event.getId()).execute();
            }
            pageToken = events.getNextPageToken();
        } while (pageToken != null);
    }

    private static Date getDueDate(final Map<String, Object> pMap) {
        try {
            final String d = (String) pMap.get("due_date");
            return new SimpleDateFormat("yyyy-MM-dd").parse(d);
        } catch (final ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getEnv(final String pVarName) {
        String ret = System.getenv(pVarName);
        if (ret == null || ret.trim().length() == 0) {
            ret = System.getProperty(pVarName);
            if (ret == null || ret.trim().length() == 0) {
                throw new IllegalStateException("環境変数 '" + pVarName + "' が設定されていません.");
            }
        }
        return ret;
    }

    private static void insertTasks(final Calendar pService, final String pCalendarId) throws IOException {
        try (final InputStream in = new FileInputStream(getEnv("TASK_JSON_FILE_PATH")); //
                final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)); //
        ) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> m = pService.getJsonFactory().fromString(line, Map.class);

                final EventDateTime d = new EventDateTime() //
                        .setDate(new DateTime((String) m.get("due_date"))) //
                        .setTimeZone("Asia/Tokyo") //
                        ;

                final Event e = new Event() //
                        .setSummary((String) m.get("title")) //
                        .setDescription((String) m.get("description"))//
                        .setStart(d) //
                        .setEnd(d) //
                        ;
                pService.events().insert(pCalendarId, e).execute();
            }
        }
    }

}
