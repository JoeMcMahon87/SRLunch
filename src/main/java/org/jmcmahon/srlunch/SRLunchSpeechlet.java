/**
 * Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not
 * use this file except in compliance with the License. A copy of the License is
 * located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.jmcmahon.srlunch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Queries the Sage Dining menu for Stone Ridge School and returns back the
 * lunch menu options for a given day
 */
public class SRLunchSpeechlet implements Speechlet {

    private static final Logger log = LoggerFactory.getLogger(SRLunchSpeechlet.class);

    /**
     * URL for Sage Dining menu
     */
    private static final String URL_PREFIX = "http://www.sagedining.com/intranet/apps/mb/pubasynchhandler.php?unitId=S0073&mbMenuCardinality=1&_=1451222936630";

    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat spokenFormat = new SimpleDateFormat("EEEEE MMMMM d yyyy");

    private static final String SESSION_TEXT = "text";
    private static final String SESSION_STAGE = "stage";
    private static final String SESSION_MONTH = "month";
    private static final String SESSION_DATE = "date";

    private static final int LOWER_SCHOOL = 0;

    private static final int MIDDLE_SCHOOL = 1;

    private static final Map<Integer, String> stationMap = new HashMap<>();

    /**
     * Constant defining session attribute key for the intent slot key for the
     * date of events.
     */
    private static final String SLOT_DAY = "day";

    /**
     * Array of month names.
     */
    private static final String[] MONTH_NAMES = {
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    };

    static {
        stationMap.put(3, "Entrees");
        stationMap.put(0, "Soups");
        stationMap.put(1, "Salads");
        stationMap.put(2, "Deli");
        stationMap.put(8, "Fruit and Dessert");

    }

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if (null != intentName) {
            switch (intentName) {
                case "GetMenuIntent":
                    return handleMenuRequest(intent, session);
                case "GetNextMenuIntent":
                    return handleNextMenuRequrest(intent, session);
                case "AMAZON.HelpIntent":
                    // Create the plain text output.
                    String speechOutput = "With Stone Ridge Lunch, you can get"
                            + " the menu Sage Dining is serving at Stone Ridge"
                            + " For example, you could say today, tomorrow, "
                            + " or a specific date like October seventh"
                            + " Now, which day do you want?";

                    String repromptText = "Which day do you want?";

                    return newAskResponse(speechOutput, false, repromptText, false);
                case "AMAZON.CancelIntent": {
                    PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
                    outputSpeech.setText("Goodbye");

                    return SpeechletResponse.newTellResponse(outputSpeech);
                }
                default:
                    throw new SpeechletException("Invalid Intent");
            }
        }
        throw new SpeechletException("Invalid Intent");
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any session cleanup logic would go here
    }

    /**
     * Function to handle the onLaunch skill behavior.
     *
     * @return SpeechletResponse object with voice/card response to return to
     * the user
     */
    private SpeechletResponse getWelcomeResponse() {
        String speechOutput = "Stone Ridge Lunch. For which day do you want the menu?";
        // If the user either does not reply to the welcome message or says something that is not
        // understood, they will be prompted again with this text.
        String repromptText = "With Stone Ridge Lunch, you can get"
                + " the menu Sage Dining is serving at Stone Ridge"
                + " For example, you could say today, tomorrow, "
                + " or a specific date like October seventh"
                + " Now, which day do you want?";

        return newAskResponse(speechOutput, false, repromptText, false);
    }

    /**
     * Function to accept an intent containing a Day slot (date object) and
     * return the Calendar representation of that slot value. If the user
     * provides a date, then use that, otherwise use today. The date is in
     * server time, not in the user's time zone. So "today" for the user may
     * actually be tomorrow.
     *
     * @param intent the intent object containing the day slot
     * @return the Calendar representation of that date
     */
    private Calendar getCalendar(Intent intent) {
        Slot daySlot = intent.getSlot(SLOT_DAY);
        Date date = new Date();
        Calendar calendar = Calendar.getInstance();
        if (daySlot != null && daySlot.getValue() != null) {
            try {
                date = formatter.parse(daySlot.getValue());
            } catch (ParseException ex) {
                throw new RuntimeException("The date given is not a date this service handles.  Try today, tomorrow or a specific month and day");
            }
        } else {
            date = new Date();
        }
        calendar.setTime(date);
        return calendar;
    }

    /**
     * Prepares the speech to reply to the user. Obtain menu from Sage Dining
     * for the date specified by the user (or for today's date, if no date is
     * specified), and return the info in both speech and SimpleCard format.
     *
     * @param intent the intent object which contains the date slot
     * @param session the session object
     * @return SpeechletResponse object with voice/card response to return to
     * the user
     */
    private SpeechletResponse handleMenuRequest(Intent intent, Session session) {
        try {
            Calendar calendar = getCalendar(intent);
            String month = MONTH_NAMES[calendar.get(Calendar.MONTH)];
            String date = formatter.format(calendar.getTime());

            String speechPrefixContent = "<p>Entrees for " + month + " " + date + "</p> ";
            String cardPrefixContent = "Entrees for " + month + " " + date + ", ";
            String cardTitle = "For " + month + " " + date;
            Map<String, List<String>> menuItems;
            String speechOutput;

            try {
                menuItems = getJsonMenuItemsFromSage(date);
                if (menuItems.isEmpty()) {
                    speechOutput
                            = "There is a problem connecting to Sage Dining at this time."
                            + " Please try again later.";

                    // Create the plain text output
                    SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                    outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");

                    return SpeechletResponse.newTellResponse(outputSpeech);
                } else {
                    StringBuilder speechOutputBuilder = new StringBuilder();
                    speechOutputBuilder.append(speechPrefixContent);
                    StringBuilder cardOutputBuilder = new StringBuilder();
                    cardOutputBuilder.append(cardPrefixContent);
                    if (menuItems.get("Entrees") != null) {
                        for (String entree : menuItems.get("Entrees")) {
                            speechOutputBuilder.append("<p>");
                            speechOutputBuilder.append(entree);
                            speechOutputBuilder.append("</p> ");
                            cardOutputBuilder.append(entree);
                            cardOutputBuilder.append("\n");
                        }

                        speechOutputBuilder.append(" Want more menu items?");
                        cardOutputBuilder.append(" Want more menu items?");
                        speechOutput = speechOutputBuilder.toString();

                        String repromptText
                                = "With Stone Ridge Lunch, you can get"
                                + " the menu Sage Dining is serving at Stone Ridge"
                                + " For example, you could say today, tomorrow, "
                                + " or a specific date like October seventh"
                                + " Now, which day do you want?";

                        // Create the Simple card content.
                        SimpleCard card = new SimpleCard();
                        card.setTitle(cardTitle);
                        card.setContent(cardOutputBuilder.toString());

                        session.setAttribute(SESSION_TEXT, menuItems);
                        session.setAttribute(SESSION_STAGE, 1);
                        session.setAttribute(SESSION_MONTH, month);
                        session.setAttribute(SESSION_DATE, date);
                        SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
                        response.setCard(card);
                        return response;
                    } else {
                        speechOutput
                                = "There were no entrees found for that date"
                                + " Please try again later.";

                        // Create the plain text output
                        SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                        outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");

                        return SpeechletResponse.newTellResponse(outputSpeech);
                    }
                }
            } catch (RuntimeException re) {
                speechOutput = re.getMessage();

                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent(speechOutput);
                SpeechletResponse response = SpeechletResponse.newTellResponse(outputSpeech);
                response.setCard(card);

                return response;
            }
        } catch (RuntimeException re) {
                String speechOutput = re.getMessage();

                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                SimpleCard card = new SimpleCard();
                card.setTitle("Error processing");
                card.setContent(speechOutput);
                SpeechletResponse response = SpeechletResponse.newTellResponse(outputSpeech);
                response.setCard(card);

                return response;
        }
    }

    /**
     * Download JSON-formatted list of menu items from Sage Dining for a defined
     * day/date, and return a String array of the items.
     *
     * @param date
     * @return
     */
    public Map<String, List<String>> getJsonMenuItemsFromSage(String date) {
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        String text = "";
        try {
            String line;
            URL url = new URL(URL_PREFIX);
            inputStream = new InputStreamReader(url.openStream(), Charset.forName("US-ASCII"));
            bufferedReader = new BufferedReader(inputStream);
            StringBuilder builder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            text = builder.toString();
        } catch (IOException e) {
            // reset text variable to a blank string
            text = "";
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }
        return parseJson(text, date);
    }

    /**
     * Index 1: Cycle Days: 0-11, 12 = Daily Offerings Index 2: Days of Week 0-6
     * starting on Sunday Index 3: 0, 2, 3 - blank, 1 - menu Index 4: Stations 0
     * - Stock exchange (soups) 1 - Improvisations (salads) 2 - Classic Cuts
     * (deli) 3 - Main Ingredient 8 - Baking Co
     *
     * @param text
     * @param month
     * @param date
     * @return
     */
    private Map<String, List<String>> parseJson(String text, String date) {
        Map<String, List<String>> retval = new HashMap<>();
        try {
            JSONObject obj = new JSONObject(text);
            Long menuFirstDate = ((JSONObject) obj.getJSONArray("menuList").get(MIDDLE_SCHOOL)).getLong("menuFirstDate");
            // Get the cycle length from the JSON
            int cycleLength = 12;
            int index = calculateOffset(menuFirstDate, date);
            int index1 = (int) (index / 10);
            int index2 = (int) (index % 10);
            if (index1 < cycleLength) {
                if ((index2 > 0) && (index2 < 6)) {
                    JSONArray items = obj.getJSONObject("menu").getJSONObject("menu").getJSONArray("items");
                    JSONArray days = items.getJSONArray(index1);
                    JSONArray menu = days.getJSONArray(index2);
                    JSONArray allStations = menu.getJSONArray(1);
                    Set<Integer> stations = stationMap.keySet();
                    for (int station : stations) {
                        JSONArray menuItems = allStations.getJSONArray(station);
                        List<String> tempItems = new ArrayList<>();
                        for (int loop = 0; loop < menuItems.length(); loop++) {
                            String item = ((JSONObject) menuItems.get(loop)).get("a").toString();
                            item = item.replaceAll("&", "and");
                            tempItems.add(item);
                        }
                        retval.put(stationMap.get(station), tempItems);
                    }
                } else {
                    String errorDate = spokenFormat.format(formatter.parse(date));
                    throw new RuntimeException("Food is not served on " + errorDate);
                }
            } else {
                String errorDate = spokenFormat.format(formatter.parse(date));
                throw new RuntimeException("There is no menu information for " + errorDate);
            }
        } catch (JSONException | ParseException ex) {
            throw new RuntimeException(date + " is not a date this service handles.  Try today, tomorrow or a specific month and day");
        }

        return retval;
    }

    /**
     * Take the date in yyyy-mm-dd form and the given menu start date and find
     * the week offset and day of week
     *
     * @param date
     * @return int Encoded as XXY - where XX = week number and Y = day of week
     */
    private int calculateOffset(Long menuFirstDate, String date) throws ParseException {
        Long targetDate = formatter.parse(date).getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(targetDate);
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysDiff = (int) ((targetDate - (menuFirstDate * 1000)) / (1000 * 60 * 60 * 24));
        int weeksDiff = (int) (daysDiff / 7);

        return (weeksDiff * 10) + dow;
    }

    /**
     * Wrapper for creating the Ask response from the input strings.
     *
     * @param stringOutput the output to be spoken
     * @param isOutputSsml whether the output text is of type SSML
     * @param repromptText the reprompt for if the user doesn't reply or is
     * misunderstood.
     * @param isRepromptSsml whether the reprompt text is of type SSML
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
            String repromptText, boolean isRepromptSsml) {
        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }

        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

    /**
     * Process the next group of foods
     *
     * @param intent
     * @param session
     * @return
     */
    private SpeechletResponse handleNextMenuRequrest(Intent intent, Session session) {
        Integer stage = (Integer) session.getAttribute(SESSION_STAGE);
        if ((stage != null) && (stage >= 1)) {
            String month = (String) session.getAttribute(SESSION_MONTH);
            String date = (String) session.getAttribute(SESSION_DATE);
            Map<String, List<String>> menuItems = (Map<String, List<String>>) session.getAttribute(SESSION_TEXT);
            String speechOutput = "";
            String cardOutput = "";
            String cardTitle = "For " + month + " " + date;
            String speechPrefixContent;
            String cardPrefixContent;
            StringBuilder speechOutputBuilder = new StringBuilder();
            StringBuilder cardOutputBuilder = new StringBuilder();

            switch (stage) {
                case 1:
                    // Process Soups
                    speechPrefixContent = "<p>Soups for " + month + " " + date + "</p> ";
                    cardPrefixContent = "Soups for " + month + " " + date + ", ";
                    speechOutputBuilder.append(speechPrefixContent);
                    cardOutputBuilder.append(cardPrefixContent);
                    if (menuItems.get("Soups") != null) {
                        for (String soup : menuItems.get("Soups")) {
                            speechOutputBuilder.append("<p>");
                            speechOutputBuilder.append(soup);
                            speechOutputBuilder.append("</p> ");
                            cardOutputBuilder.append(soup);
                            cardOutputBuilder.append("\n");
                        }

                        speechOutputBuilder.append(" Want more menu items?");
                        cardOutputBuilder.append(" Want more menu items?");
                        session.setAttribute(SESSION_STAGE, (stage + 1));
                        speechOutput = speechOutputBuilder.toString();
                        cardOutput = cardOutputBuilder.toString();
                    }
                    break;
                case 2:
                    // Process Salads
                    speechPrefixContent = "<p>Salads for " + month + " " + date + "</p> ";
                    cardPrefixContent = "Salads for " + month + " " + date + ", ";
                    speechOutputBuilder.append(speechPrefixContent);
                    cardOutputBuilder.append(cardPrefixContent);
                    if (menuItems.get("Salads") != null) {
                        for (String salad : menuItems.get("Salads")) {
                            speechOutputBuilder.append("<p>");
                            speechOutputBuilder.append(salad);
                            speechOutputBuilder.append("</p> ");
                            cardOutputBuilder.append(salad);
                            cardOutputBuilder.append("\n");
                        }

                        speechOutputBuilder.append(" Want more menu items?");
                        cardOutputBuilder.append(" Want more menu items?");
                        session.setAttribute(SESSION_STAGE, (stage + 1));
                        speechOutput = speechOutputBuilder.toString();
                        cardOutput = cardOutputBuilder.toString();
                    }
                    break;
                case 3:
                    // Process Deli
                    speechPrefixContent = "<p>Deli items for " + month + " " + date + "</p> ";
                    cardPrefixContent = "Deli items for " + month + " " + date + ", ";
                    speechOutputBuilder.append(speechPrefixContent);
                    cardOutputBuilder.append(cardPrefixContent);
                    if (menuItems.get("Deli") != null) {
                        for (String deli : menuItems.get("Deli")) {
                            speechOutputBuilder.append("<p>");
                            speechOutputBuilder.append(deli);
                            speechOutputBuilder.append("</p> ");
                            cardOutputBuilder.append(deli);
                            cardOutputBuilder.append("\n");
                        }

                        speechOutputBuilder.append(" Want more menu items?");
                        cardOutputBuilder.append(" Want more menu items?");
                        session.setAttribute(SESSION_STAGE, (stage + 1));
                        speechOutput = speechOutputBuilder.toString();
                        cardOutput = cardOutputBuilder.toString();
                    }
                    break;
                case 4:
                    // Process Fruit and Desserts
                    speechPrefixContent = "<p>Fruit and Dessert for " + month + " " + date + "</p> ";
                    cardPrefixContent = "Fruit and Dessert for " + month + " " + date + ", ";
                    speechOutputBuilder.append(speechPrefixContent);
                    cardOutputBuilder.append(cardPrefixContent);
                    if (menuItems.get("Fruit and Dessert") != null) {
                        for (String fd : menuItems.get("Fruit and Dessert")) {
                            speechOutputBuilder.append("<p>");
                            speechOutputBuilder.append(fd);
                            speechOutputBuilder.append("</p> ");
                            cardOutputBuilder.append(fd);
                            cardOutputBuilder.append("\n");
                        }
                        speechOutput = speechOutputBuilder.toString();
                        cardOutput = cardOutputBuilder.toString();
                    }
                    break;
                default:
                    break;
            }
            String repromptText = "Do you want to know more menu items on this date?";

            // Create the Simple card content.
            SimpleCard card = new SimpleCard();
            card.setTitle(cardTitle);
            card.setContent(cardOutput);

            SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
            response.setCard(card);
            response.setShouldEndSession((stage >= 4));
            return response;
        } else {
            return getWelcomeResponse();
        }
    }
}
