/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.jmcmahon.srlunch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
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
import java.util.logging.Level;

/**
 * This sample shows how to create a Lambda function for handling Alexa Skill requests that:
 * 
 * <ul>
 * <li><b>Web service</b>: communicate with an external web service to get events for specified days
 * in history (Wikipedia API)</li>
 * <li><b>Pagination</b>: after obtaining a list of events, read a small subset of events and wait
 * for user prompt to read the next subset of events by maintaining session state</li>
 * <p>
 * <li><b>Dialog and Session state</b>: Handles two models, both a one-shot ask and tell model, and
 * a multi-turn dialog model</li>
 * <li><b>SSML</b>: Using SSML tags to control how Alexa renders the text-to-speech</li>
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <p>
 * <b>One-shot model</b>
 * <p>
 * User: "Alexa, ask History Buff what happened on August thirtieth."
 * <p>
 * Alexa: "For August thirtieth, in 2003, [...] . Wanna go deeper in history?"
 * <p>
 * User: "No."
 * <p>
 * Alexa: "Good bye!"
 * <p>
 * 
 * <b>Dialog model</b>
 * <p>
 * User: "Alexa, open History Buff"
 * <p>
 * Alexa: "History Buff. What day do you want events for?"
 * <p>
 * User: "August thirtieth."
 * <p>
 * Alexa: "For August thirtieth, in 2003, [...] . Wanna go deeper in history?"
 * <p>
 * User: "Yes."
 * <p>
 * Alexa: "In 1995, Bosnian war [...] . Wanna go deeper in history?"
 * <p>
 * User: "No."
 * <p>
 * Alexa: "Good bye!"
 * <p>
 */
public class SRLunchSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(SRLunchSpeechlet.class);

    /**
     * URL for Sage Dining menu: http://www.sagedining.com/intranet/apps/mb/pubasynchhandler.php?unitId=S0073&mbMenuCardinality=1&_=1451222936630
     */
    private static final String URL_PREFIX = "http://www.sagedining.com/intranet/apps/mb/pubasynchhandler.php?unitId=S0073&mbMenuCardinality=1&_=1451222936630";
    
    private static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    
    private static final String SESSION_TEXT = "text";
    /**
     * Constant defining session attribute key for the intent slot key for the date of events.
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

        if (null != intentName) switch (intentName) {
            case "GetFirstEventIntent":
                return handleFirstEventRequest(intent, session);
            case "AMAZON.HelpIntent":
                // Create the plain text output.
                String speechOutput = "With Stone Ridge Lunch, you can get"
                        + " the menu Sage Dining is serving at Stone Ridge"
                        + " For example, you could say today, tomorrow, "
                        + " or a specific date like October seventh"
                        + " Now, which day do you want?";
                
                String repromptText = "Which day do you want?";
                
                return newAskResponse(speechOutput, false, repromptText, false);
            case "AMAZON.StopIntent":
            {
                PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
                outputSpeech.setText("Goodbye");
                
                return SpeechletResponse.newTellResponse(outputSpeech);
            }
            case "AMAZON.CancelIntent":
            {
                PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
                outputSpeech.setText("Goodbye");
                
                return SpeechletResponse.newTellResponse(outputSpeech);
            }
            default:
                throw new SpeechletException("Invalid Intent");
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
     * @return SpeechletResponse object with voice/card response to return to the user
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
     * Function to accept an intent containing a Day slot (date object) and return the Calendar
     * representation of that slot value. If the user provides a date, then use that, otherwise use
     * today. The date is in server time, not in the user's time zone. So "today" for the user may
     * actually be tomorrow.
     * 
     * @param intent
     *            the intent object containing the day slot
     * @return the Calendar representation of that date
     */
    private Calendar getCalendar(Intent intent) {
        Slot daySlot = intent.getSlot(SLOT_DAY);
        Date date;
        Calendar calendar = Calendar.getInstance();
        if (daySlot != null && daySlot.getValue() != null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                date = dateFormat.parse(daySlot.getValue());
            } catch (ParseException e) {
                date = new Date();
            }
        } else {
            date = new Date();
        }
        calendar.setTime(date);
        return calendar;
    }

    /**
     * Prepares the speech to reply to the user. Obtain events from Wikipedia for the date specified
     * by the user (or for today's date, if no date is specified), and return those events in both
     * speech and SimpleCard format.
     * 
     * @param intent
     *            the intent object which contains the date slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleFirstEventRequest(Intent intent, Session session) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = getCalendar(intent);
        String month = MONTH_NAMES[calendar.get(Calendar.MONTH)];
        String date = fmt.format(calendar.getTime());
        java.util.logging.Logger.getLogger(SRLunchSpeechlet.class.getName()).log(Level.SEVERE, "Handling for {0}", date);

        String speechPrefixContent = "<p>For " + month + " " + date + "</p> ";
        String cardPrefixContent = "For " + month + " " + date + ", ";
        String cardTitle = "Menu for " + month + " " + date;

        ArrayList<String> entrees = getJsonMenuItemsFromSage(date);
        String speechOutput;
        if (entrees.isEmpty()) {
            speechOutput =
                    "There is a problem connecting to Sage Dining at this time."
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
            for (String entree : entrees) {
                speechOutputBuilder.append("<p>");
                speechOutputBuilder.append(entree);
                speechOutputBuilder.append("</p> ");
                cardOutputBuilder.append(entree);
                cardOutputBuilder.append("\n");
            }
           
            speechOutput = speechOutputBuilder.toString();

            String repromptText =
                    "With Stone Ridge Lunch, you can get"
                    + " the menu Sage Dining is serving at Stone Ridge"
                    + " For example, you could say today, tomorrow, "
                    + " or a specific date like October seventh"
                    + " Now, which day do you want?";

            // Create the Simple card content.
            SimpleCard card = new SimpleCard();
            card.setTitle(cardTitle);
            card.setContent(cardOutputBuilder.toString());

            // After reading the first 3 events, set the count to 3 and add the events
            // to the session attributes
            session.setAttribute(SESSION_TEXT, entrees);

            SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
            response.setCard(card);
            return response;
        }
    }

    /**
     * Download JSON-formatted list of menu items from Sage Dining for a defined day/date, and
     * return a String array of the items.
     * @param date
     * @return 
     */
    public ArrayList<String> getJsonMenuItemsFromSage(String date) {
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
     * Index 1: Cycle Days: 0-11, 12 = Daily Offerings
     * Index 2: Days of Week 0-6 starting on Sunday
     * Index 3: 0, 2, 3 - blank, 1 - menu
     * Index 4: Stations
     *  0 - Stock exchange (soups)
     *  1 - Improvisations
     *  2 - Classic Cuts (deli)
     *  3 - Main Ingredient
     *  8 - Baking Co
     *  
     * @param text
     * @param month
     * @param date
     * @return 
     */
    private ArrayList<String> parseJson(String text, String date) {
        ArrayList<String> retval = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(text);
            Long menuFirstDate = ((JSONObject)obj.getJSONArray("menuList").get(1)).getLong("menuFirstDate");
            int index = calculateOffset(menuFirstDate, date);
            int index1 = (int)(index / 10) + 1;
            int index2 = (int)(index % 10);
            JSONArray items = obj.getJSONObject("menu").getJSONObject("menu").getJSONArray("items");
            JSONArray days = items.getJSONArray(index1);
            JSONArray menu = days.getJSONArray(index2);
            JSONArray stations = menu.getJSONArray(1);
            JSONArray mains = stations.getJSONArray(3);
            for (int loop = 0; loop < mains.length(); loop++) {
                retval.add( ((JSONObject)mains.get(loop)).get("a").toString() );
            }
        } catch (JSONException | ParseException ex) {
            java.util.logging.Logger.getLogger(SRLunchSpeechlet.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return retval;
    }
    
    /**
     * Take the date in yyyy-mm-dd form and the given menu start date
     * and find the week offset and day of week
     * @param date
     * @return int Encoded as XXY - where XX = week number and Y = day of week
     */
    private int calculateOffset(Long menuFirstDate, String date) throws ParseException {
        Long targetDate = formatter.parse(date).getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(targetDate);
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysDiff = (int)((targetDate - (menuFirstDate * 1000)) / (1000 * 60 * 60 * 24));
        int weeksDiff = (int) (daysDiff / 7);
        
        return (weeksDiff * 10) + dow;
    }
    
    /**
     * Wrapper for creating the Ask response from the input strings.
     * 
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml
     *            whether the output text is of type SSML
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @param isRepromptSsml
     *            whether the reprompt text is of type SSML
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

}