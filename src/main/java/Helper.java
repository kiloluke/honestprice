import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.mail.MessagingException;
import java.io.IOException;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

import static j2html.TagCreator.td;
import static j2html.TagCreator.tr;

public class Helper {

    private static final Helper INSTANCE = new Helper();
    private float totalPrice;
    Session session;

    static Helper getInstance() {
        return INSTANCE;
    }

    private ArrayList<String> mailSendingList;
    private Map<String, String> notFoundDetailTypes;
    private Gmail gmail;

    Helper() {
        gmail = new Gmail();
        mailSendingList = new ArrayList<>();
        notFoundDetailTypes = new LinkedHashMap<>();
    }


    void amazonSearch(ArrayList<String> details) {

        notFoundDetailTypes.clear();

        notFoundDetailTypes.put("Handlebar", "$5");
        notFoundDetailTypes.put("Wheel", "$12");
        notFoundDetailTypes.put("Seat", "$7");
        notFoundDetailTypes.put("Chain", "$5");
        notFoundDetailTypes.put("Hub", "$12");
        notFoundDetailTypes.put("Brake", "$6");
        notFoundDetailTypes.put("Tyre", "$8");
        notFoundDetailTypes.put("Grip", "$2");

        if (mailSendingList.size() > 30) {
            sendList();
        }


        for (String detail : details) {
            String detailFromRequest = detail;
            detail = detail.replace("^", "").replace(",", "").replace("\\t", " ");
            String detailLC = detail.toLowerCase();
            if (detailLC.contains("colour") || detailLC.contains("color") || detailLC.contains("weight") || detailLC.contains("not included")) {
                sendBack("Warning", detail, "0");
                continue;
            }
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.38 Safari/537.36";



            final Document googleSearchResult;
            try {
                googleSearchResult = Jsoup.connect("https://google.com/search?q=" + detail.toLowerCase()
                        .replace(" ", "+").replaceAll("\\t", "+").trim() + "+amazon").userAgent(userAgent).timeout(0).get();


                for (Element googleLink : googleSearchResult.select("h3.r a")) {
                    if (googleLink.attr("href").contains("amazon") && !googleLink.attr("href").contains(".pdf")
                            && !googleLink.attr("href").contains("amazonaws")) {
                        try {
                            Document amazonPage = Jsoup.connect(googleLink.attr("href")).userAgent(userAgent).timeout(500).get();
                            String priceString;

                            try {
                                priceString = amazonPage.select("span.a-size-medium.a-color-price").first().text();
                            } catch (NullPointerException n) {
                                try {
                                    priceString = amazonPage.select("span.a-color-price").first().text();
                                } catch (NullPointerException n2) {
                                    break;
                                }
                            }

                            boolean isItemBikePart = false;
                            boolean isItemBike = false;
                            boolean isDetailGroupRecognized = false;

                            for (Element element1 : amazonPage.select("a.a-link-normal.a-color-tertiary")) {

//                                if (!isDetailGroupRecognized) {
//                                    for (String requiredDetail : notFoundDetailTypes.keySet()) {
//                                        if (element1.text().toLowerCase().contains(requiredDetail.toLowerCase())
//                                                || itemLC.contains(requiredDetail.toLowerCase())) {
//                                            notFoundDetailTypes.remove(requiredDetail);
//                                            isDetailGroupRecognized = true;
//                                            break;
//                                        }
//                                    }
//                                }
                                if (element1.text().equalsIgnoreCase("Cycling")) {
                                    isItemBikePart = true;
                                }
                                if (element1.text().equalsIgnoreCase("Bikes")) {
                                    isItemBike = true;
                                    break;
                                }

                            }

                            if (!isItemBikePart || isItemBike) {
                                break;
                            }

                            StringBuilder priceFormatted = new StringBuilder();
                            Element group = amazonPage.select("a.a-link-normal.a-color-tertiary").last();
                            if (priceString.length() > 6) {
                                priceFormatted.append(priceString.substring(0, 6));
                                if (!priceFormatted.toString().matches(".*[0-9].*")) {
                                    break;
                                }

                            } else if (priceString.contains("CDN$")) {
                                priceFormatted.append(priceString.split(" ")[1]);
                            } else {
                                priceFormatted.append(priceString);
                            }

                            requiredDetailTest(group.text());
                            requiredDetailTest(detailFromRequest.split("\\s")[0]);
                            mailSendingList.add("<a href=\"" + googleLink.attr("href") + "\">" + detail + "</a>");
                            sendBack(group.text(), detailFromRequest, priceFormatted.toString());
                            break;

                        } catch (Exception e) {
                            e.printStackTrace();
                            mailSendingList.add("<p>Error! " + e.toString() + "</p><a href=\"" + googleLink.attr("href") + "\">" + detail + "</a>");
                        }

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                mailSendingList.add("<p>Error! " + e.toString() + "</p>");
                sendBack("Error. No connection", "Try again", "-1");
            }

        }


        // NEEDS TO BE REDONE
        Set<String> keys = notFoundDetailTypes.keySet();

        if (notFoundDetailTypes.size() > 0) {
            sendHeadingBack("Not recognized parts that are required to be in a bicycle");
            notFoundDetailTypes.forEach((k, v) -> sendBack(k, " ", v));
        }
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.CEILING);
        sendHeadingBack("Total price $" + df.format(totalPrice));

    }

    private void requiredDetailTest(String detailType) {
        if (detailType.matches("T[i|y]re")) {
            removeFromNotFoundDetails("Tyre");
            removeFromNotFoundDetails("Wheel");

        } else if (detailType.contains("Handlebar")) {
            removeFromNotFoundDetails("Handlebar");
        } else if (detailType.contains("Wheel")) {

            removeFromNotFoundDetails("Tyre");
            removeFromNotFoundDetails("Hub");

        } else if (detailType.contains("Hub")) {

            removeFromNotFoundDetails("Chain");
            removeFromNotFoundDetails("Wheel");

        } else if (detailType.contains("Chain") || detailType.toLowerCase().contains("Drivetrain")){

        } else if (detailType.contains("Brake")) {
            removeFromNotFoundDetails("Brake");
        } else if (detailType.contains("Grip")) {
            removeFromNotFoundDetails("Grip");
        }

    }

    private void removeFromNotFoundDetails(String detailType) {
        Iterator<String> iterator = notFoundDetailTypes.keySet().iterator();
        while (iterator.hasNext()) {
            String dt = iterator.next();
            if (dt.equalsIgnoreCase(detailType)) {
                iterator.remove();
            }

        }

    }


    private void sendList() {
        StringBuilder stringBuilder = new StringBuilder();
        mailSendingList.forEach((message) -> stringBuilder.append(message).append("<br/>"));

        try {
            gmail.sendEmail(stringBuilder.toString());
        } catch (MessagingException e) {
            e.printStackTrace();
        }

    }


    String renderContent(String htmlFile) {
        try {
            return new String(Files.readAllBytes(Paths.get(getClass().getResource(htmlFile).toURI())), StandardCharsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            mailSendingList.add(e.toString());
        }
        return null;
    }

    private void broadcastData(String heading, String detail, String price, Session session) {

        try {
            session.getRemote().sendString(String.valueOf(new JSONObject()
                    .put("info", createHtmlMessage(heading, detail, price))));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void broadcastData(String heading, Session session) {
        try {
            session.getRemote().sendString(String.valueOf(new JSONObject()
                    .put("info", createHtmlHeadingMessage(heading))));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String createHtmlMessage(String heading, String detail, String price) {
        float priceDouble;
        if (price.contains("EUR")) {
            priceDouble = (float) (Float.parseFloat(price.split(" ")[0]) * 0.9);
        } else if (price.contains("£")) {
            priceDouble = (float) (Float.parseFloat(price.substring(1)) * 1.3);
        } else if (price.contains("$")) {
            priceDouble = (float) (Float.parseFloat(price.substring(1)) * 1.2);
        } else if (price.length() < 1) {
            priceDouble = 0;
        } else if (price.contains("CDN$")) {
            priceDouble = (float) (Float.parseFloat(price.split(" ")[1]) * 0.7);
        } else {
            priceDouble = Float.parseFloat(price);
        }


        totalPrice += priceDouble;
        return tr().withClass("itemAndPrice")
                .with(
                        td(heading).withClass("heading"),
                        td(detail).withClass("detail"),
                        td(price).withClass("price")
                ).render();
    }

    private String createHtmlHeadingMessage(String heading) {
        return tr().withClass("dataHeader").attr("colspan", "3")
                .with(
                        td(heading)
                ).render();
    }

    private void sendBack(String heading, String detail, String price) {
        broadcastData(heading, detail, price, session);
    }

    void sendHeadingBack(String heading) {
        broadcastData(heading, session);
    }

    void setTotalPrice(float totalPrice) {
        this.totalPrice = totalPrice;
    }
}