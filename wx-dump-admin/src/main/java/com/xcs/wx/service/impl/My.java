package com.xcs.wx.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import com.xcs.wx.util.DateFormatUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class My {
    private static final String URL_MESSAGE = "http://localhost:5030/api/v1/chatlog?talker=%s&time=%s&limit=1000&format=%s";
    private static final String URL_CONTACT = "http://localhost:5030/api/v1/contact?format=json";
    private static final String URL_CHATROOM = "http://localhost:5030/api/v1/chatroom?format=json";
    private static final String URL_SESSION = "http://localhost:5030/api/v1/session?format=json";
    private static final String WX_CFG = "D:/文件/read_time.json";
    private static final int size = 500;
    private static String IPV6;

    private static Map<String, String> CONTACT = new HashMap<>();
    private static Map<String, Long> READ_TIME = new HashMap<>();
    private static String _debugUrl;


    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JavaTimeModule());
    }

    public static void main(String[] args) throws Exception {
        READ_TIME = loadWxTime();

        // 绑定端口
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        // 注册路径和处理器
        server.createContext("/", exchange -> {
            try {
                URI requestURI = exchange.getRequestURI();
                String query = requestURI.getQuery();

                String response = switch (requestURI.getPath()) {
                    case "/session" -> {
                        //ROOMS = StreamSupport.stream(objectMapper.readTree(get(URL_CHATROOM)).get("items").spliterator(), false).collect(Collectors.toMap(x -> x.get("name").asText(), x -> Stream.of(x.get("remark"), x.get("nickName"), x.get("name")).map(JsonNode::asText).filter(y -> !y.isEmpty()).findFirst().orElse(null)));
                        CONTACT = StreamSupport.stream(mapper.readTree(httpGet(URL_CONTACT)).get("items").spliterator(), false)
                                .collect(Collectors.toMap(x -> x.get("userName").asText(), x -> Stream.of(x.get("remark"), x.get("nickName"), x.get("userName")).map(JsonNode::asText).filter(y -> !y.isEmpty()).findFirst().orElse("")));
                        IPV6 = getIpv6();

                        yield buildSessionHtml(mapper.convertValue(mapper.readTree(httpGet(URL_SESSION)).get("items"), new TypeReference<List<Session>>() {
                        }));
                        //yield buildSessionHtml(((ArrayNode) objectMapper.readTree(get(URL_CHATROOM)).get("items")));
                    }
                    case "/message" -> {
                        Map<String, String> q = Arrays.stream(query.split("&")).map(s -> s.split("=", 2)) // 拆成 [key, value]
                                .collect(Collectors.toMap(x -> x[0], x -> x[1]));

                        String timeQ = Optional.ofNullable(q.get("time")).orElse(Instant.ofEpochMilli(READ_TIME.getOrDefault(q.get("talker"), 0L)).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                                + "~" + "2100-01-01";
                        String resp = httpGet(URL_MESSAGE.formatted(q.get("talker"), timeQ, "json"/*json*/));

                        resp = mapper.writeValueAsString(StreamSupport.stream(mapper.readTree(resp).spliterator(), false)
                                .filter(j ->
                                        q.get("time") != null ||
                                                j.get("seq").asLong() > READ_TIME.getOrDefault(q.get("talker"), 0L)).toList());
                        //List<Message> messages = mapper.readValue(resp, new TypeReference<List<Message>>() {})
                        //        .stream().filter(x -> x.seq > READ_TIME.getOrDefault(q.get("talker"), 0L)).toList();
                        //buildMessageHtml(messages);
                        yield resp;
                    }
                    case "/read-time" -> {
                        Map<String, String> q = Arrays.stream(query.split("&")).map(s -> s.split("=", 2)) // 拆成 [key, value]
                                .collect(Collectors.toMap(x -> x[0], x -> x[1]));

                        if (Long.parseLong(q.get("time")) > READ_TIME.getOrDefault(q.get("talker"), 0L)) {
                            READ_TIME.put(q.get("talker"), Long.valueOf(q.get("time")));
                            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(WX_CFG), READ_TIME);
                        }
                        yield "";
                    }
                    case "/message.html" -> Files.readString(Path.of("D:/文件/message.html"));
                    case "/debug" -> _debugUrl.replace("localhost:5030", "[" + IPV6 + "]:8082");

                    default -> "Not Found";
                };

                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 启动服务
        server.start();
        System.out.println("Server started at http://localhost:8080/");

        listenSendTelegram();
    }

    private static String httpGet(String url) {
        _debugUrl = url;
        // 构造 GET 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return response.body();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String senderName, boolean isSelf, String content, JsonNode contents, int type, int subType,
                          OffsetDateTime time, long seq) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(String userName, String remark, String nickName, String content, long nOrder) {
    }

    public static String buildMessageHtml(List<Message> msgList) {
        String html = "";

        for (int i = 0; i < msgList.size(); i++) {
            Message x = msgList.get(i);

            String str = "";
            if (i == 0 || !x.senderName.equals(msgList.get(i - 1).senderName)) {
                str += "<div class=\"nickname\">" + "●" + x.senderName + "</div>";
            }

            str += "<div class=\"msg" + (x.type() != 10000 ? (x.isSelf ? " self" : "") : " sys") + "\">";

            String msg = x.contents == null ? "" : switch (x.type()) {
                //case 1文字 -> x.content();
                //case 3 -> "<img src=\"http://[" + IPV6 + "]:8082/image/" + contents.get("md5").asText() + "," + x.contents.get("path").asText() + "\">";
                case 3, 43 /*图片,视频*/ -> {
                    String param = StreamSupport.stream(x.contents.spliterator(), false).map(JsonNode::asText).collect(Collectors.joining(","));
                    yield "<img src=\"http://[%s]:8082/%s/%s\">".formatted(IPV6, Map.of(3, "image", 43, "video").get(x.type), param);
                }
                case 34 /*语音*/ ->
                        "<audio src=\"" + "http://[%s]:8082/voice/".formatted(IPV6) + x.contents.get("voice").asText() + "\" controls preload=\"none\"></audio>";
                case 47 /*表情*/ ->
                        "<img style=\"max-width: 150px\" src=\"" + x.contents.get("cdnurl").asText() + "\">";
                case 49 /*分享*/ -> switch (x.subType) {
                    case 1, 4/*链接分享*/, 5/*链接分享*/ ->
                            "[链接]<a href=\"" + x.contents.get("url").asText() + "\">" + x.contents.get("title").asText() + "/n" + x.contents.get("desc").asText() + "</a>";
                    case 33/*小程序*/, 51 /*视频号*/ ->
                            "[小程序]<a href=\"" + x.contents.get("url").asText() + "\">" + x.contents.get("title").asText() + "</a>";
//                    case  ->

                    case 57 ->
                            "<blockquote>" + x.contents.path("refer").get("senderName").asText() + ": " + x.contents.path("refer").get("content").asText() + "</blockquote>"
                            ;//+ x.content();
                    default ->
                            "<div><small><i>【debug】" + x.type + " " + x.subType + x.content() + x.contents + "</i></small></div>";
                };
                default ->
                        "<div><small><i>【debug】" + x.type + " " + x.subType + x.content() + x.contents + "</i></small></div>";// x.content();//"【未知】";// + x.type + "," + x.subType + x.content() + contents;
            };
            msg += x.content();


            /*if (x.type() == 3) {
                msg = "<img src=\"http://127.0.0.1:5030/image/" + contents.get("md5").asText() + "," + x.contents.get("path").asText() + "\">";
                //if("[视频]".equals(x.getStrContent())) {msg = "[视频]" + msg;}
            }
            if (x.getImage() != null) {
                //msg += "<img data-src=\"" + "/img/wx/" + x.getImage().substring(x.getImage().lastIndexOf("\\") + 1, x.getImage().length() - 4) + "\">";
                //msg += "<img src=\"/api/image/downloadImgFormLocal?localPath=" + URLEncodeUtil.encode(x.getImage()) + "\">";
            }

            if (x.getReferMsgContent() != null) {
                msg = "<blockquote>" + x.getReferMsgContent()*//*.replaceFirst("：\\n?", "\n")*//* + "</blockquote>" + x.getStrContent();
            }
            if (x.getEmojiUrl() != null) {
                msg = "<img src=\"" + x.getEmojiUrl() + "\">";
            }
            if (x.getWeAppInfo() != null) {
                msg += "[小程序]\n" + "<img src=\"" + x.getWeAppInfo().getWeAppPageThumbRawUrl() + "\">"
                        + "\n<b>" + x.getWeAppInfo().getSourceDisplayName() + "</b>"
                        + "\n" + x.getWeAppInfo().getTitle();
            }
            if (x.getCardLink() != null) {
                msg += "<a href=\"" + x.getCardLink().getUrl() + "\">" + x.getCardLink().getTitle() + "</a>"
                        + "\n" + Optional.ofNullable(x.getCardLink().getDes()).orElse("");
            }*/


            /*msg = msg.startsWith("【未定义】") ? StringEscapeUtils.escapeHtml4(msg) : msg;*/
            str += msg;
            str += "<div class=\"time\">" + DateFormatUtil.formatTimestamp(x.time.toEpochSecond()) + "</div><div></div>";
            //str += "<div class=\"time\">" + DateFormatUtil.formatTimestamp(x.getCreateTime()) + "</div>";
            str += "</div><div></div>";

            //已读位置标记
            if (i % 50 == 49 || i == msgList.size() - 1) {
                str += "<div class=\"time-anchor\" data-q=\"time=%s\"></div>".formatted(x.seq());
            }

            html += str;
        }


        return """
                    <html lang="zh-CN">
                    <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                    <title>{{title}}</title>
                    <style>/*微信*/
                        .nickname {
                            font-size: 0.75em;
                            color: #899;
                            margin-top: 10px;
                        }
                        .msg {
                            margin: 1px 8px 1px 13px;
                            border: 1px solid;
                            border-radius: 10px;
                            white-space: pre-wrap;
                            padding: 2px 5px;
                            display: inline-block;
                
                            overflow-wrap: break-word; word-break: break-word;
                
                            /*气泡方案二 display: block; width: fit-content 如果时间浮动则要加.msg::after {content: ""; display: block; clear: both;}*/
                            /*右下时间方案二 相对定位 .msg:relative; time:absolute; .msg::after { content: ''; display: inline-block; width: 26px;}*/                
                            position: relative;
                        }
                        .msg img {
                            max-width: 100%;
                            /*min-width: 100px; min-height: 100px; vertical-align: top;*/
                        }
                        .msg blockquote {
                            border-left: 5px solid #888;
                            font-size: 0.75em;
                            padding-left: 5px;
                            margin: 0;
                            color: #888;
                        }
                        /*.msg p::first-line {
                            color: #888
                        }*/
                
                        .msg .time {
                            color: #a6a6a6;
                            font-size: 0.65em;
                
                            /*右下角时间方案二，有问题不会底部对齐 float: right;*/
                            position: absolute; right: 5px; bottom: 0;
                        }
                        .msg .time + div {
                            display: inline-block;
                            width: 30px;
                        }
                
                        .msg.sys {
                            text-align: center;
                            color: #999;
                            font-size: 0.83em;
                            margin: 0 auto;
                            display: table;
                        }
                        .msg.self {
                            margin-left: auto;
                            display: table;
                        }
                
                        </style>
                        </head>
                
                        <body style="font-size: 1.2em">
                        <div><a href="?nextSequence={{firstSeq}}&size=-100">上一页</a></div>
                        {{body}}
                        <hr><div style="text-align: center">{{endTip}}</div>
                        </body>
                        <script src="/page.js"></script>
                        <script>
                        let timeout = null;
                
                        window.addEventListener('scroll', () => {
                            if (timeout) return; // 已经在等待中，忽略本次事件
                
                            timeout = setTimeout(() => {
                                _hook();
                                timeout = null; // 重置
                            }, 200); // 节流 200ms
                        });
                
                        function _hook() {
                            for (const el of document.querySelectorAll('.time-anchor')) {
                                const { top } = el.getBoundingClientRect();
                                if (top >= 0 && top < window.innerHeight) {
                                    fetch(`/read-time?talker=${new URLSearchParams(window.location.search).get('talker')}&${el.dataset.q}`);
                                    el.remove();
                                    return;
                                }
                            }
                        }
                        _hook();
                        </script>
                    </html>
                """.replace("{{body}}", html)
//                .replace("{{firstSeq}}", msgList.stream().findFirst().map(MsgVO::getSequence).map(String::valueOf).orElse(""))
                //.replace("{{endTip}}", msgList.size() < size ? "看完了" : "下一页" );
                .replace("{{endTip}}", false/*msgList.size() < size*/ ? "看完了" : "<p><a href=\"\">刷新继续</a></p>")
                .replace("{{title}}", String.valueOf("title"));
    }


    private static String buildMessageHtml(String text) {
        Pattern nickPattern = Pattern.compile("(.+)\\(([a-z0-9_]+)\\)\\s+(.+)");
        Pattern imgPattern = Pattern.compile("!\\[图片\\]\\((https?://[^)]+)\\)");

        String body = Stream.of(text.split("\n\n")).map(msg -> {
            AtomicBoolean first = new AtomicBoolean(true);
            return msg.lines().map(x -> {
                if (first.getAndSet(false)) {
                    Matcher matcher = nickPattern.matcher(x);
                    String name = "null";
                    if (matcher.matches()) {
                        name = matcher.group(1);   // 姓名
                        String account = matcher.group(2); // 账号
                        String time = matcher.group(3);    // 时间
                    }
                    return "●" + name;
                }
                Matcher m = imgPattern.matcher(x);
                x = m.find() ? "<img src=\"" + m.group(1) + "\">" : x;

                return "<div class=\"msg\">" + x + "</div>";
            }).collect(Collectors.joining("\n"));
        }).collect(Collectors.joining("\n\n"));


        return """
                    <html lang="zh-CN">
                    <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                    <title>{{title}}</title>
                    <style>/*微信*/
                        .nickname {
                            font-size: 0.75em;
                            color: #899;
                            margin-top: 10px;
                        }
                        .msg {
                            margin: 1px 8px 1px 13px;
                            border: 1px solid;
                            border-radius: 10px;
                            white-space: pre-wrap;
                            padding: 2px 5px;
                            display: inline-block;
                
                            overflow-wrap: break-word; word-break: break-word;
                
                            /*气泡方案二 display: block; width: fit-content 如果时间浮动则要加.msg::after {content: ""; display: block; clear: both;}*/
                            /*右下时间方案二 相对定位 .msg:relative; time:absolute; .msg::after { content: ''; display: inline-block; width: 26px;}*/                
                            position: relative;
                        }
                        .msg img {
                            min-width: 100px;
                            min-height: 100px;
                            max-width: 100%;
                            /*vertical-align: top;*/
                        }
                        .msg blockquote {
                            border-left: 5px solid #888;
                            font-size: 0.75em;
                            padding-left: 5px;
                            margin: 0;
                            color: #888;
                        }
                        /*.msg p::first-line {
                            color: #888
                        }*/
                
                        .msg .time {
                            color: #a6a6a6;
                            font-size: 0.65em;
                
                            /*右下角时间方案二，有问题不会底部对齐 float: right;*/
                            position: absolute; right: 5px; bottom: 0;
                        }
                        .msg .time + div {
                            display: inline-block;
                            width: 30px;
                        }
                
                        .sys {
                            text-align: center;
                            color: #999;
                            font-size: 0.83em;
                        }
                
                        </style>
                        </head>
                
                        <body style="font-size: 1.2em">
                        <div><a href="?nextSequence={{firstSeq}}&size=-100">上一页</a></div>
                        <pre>{{body}}</pre>
                        <hr><div style="text-align: center">{{endTip}}</div>
                        </body>
                        <script src="/page.js"></script>
                        <script>
                        let timeout = null;
                        window.addEventListener('scroll', () => {
                            if (timeout) return; // 已经在等待中，忽略本次事件
                
                            timeout = setTimeout(() => {
                                _hook();
                                timeout = null; // 重置
                            }, 200); // 节流 200ms
                        });
                
                        function _hook() {
                            for (const el of document.querySelectorAll('.time-anchor')) {
                                const { top } = el.getBoundingClientRect();
                                if (top >= 0 && top < window.innerHeight) {
                                    fetch(`/read-time?talker=${new URLSearchParams(window.location.search).get('talker')}&${el.dataset.q}`);
                                    el.remove();
                                    return;
                                }
                            }
                        }
                        _hook();
                        </script>
                    </html>
                """.replace("{{body}}", body);
    }


//    private static String buildSessionHtml1(ArrayNode sessions) {
//        /*Map<String, Long> allUnreadTimes = sessions.stream().map(SessionVO::getUserName).collect(Collectors.toMap(x -> x, x -> json.path(x).asLong()));
//
//        if (isTaskRunning.compareAndSet(false, true)) {
//            CompletableFuture.runAsync(() -> {
//                try {
//                    unreadCounts = msgRepositoryImpl__.queryUnreadCounts(allUnreadTimes);
//                } finally {
//                    isTaskRunning.set(false);
//                }
//            });
//        }*/
//
//        StringBuilder html = new StringBuilder("<table style=\"font-size: 1.2em\">");
//        sessions.forEach(x ->
//                html.append("""
//                        <tr onclick="style.background='#bbb'">
//                            <td><a style="display: block; max-width: 60vw; white-space: nowrap;" href="message?talker=%s" >%s</a></td>
//                            <td style="text-align:right"><span style="background: black; color: white; border-radius: 15px;">%s</span></td>
//                            <td style="text-align:right">%s</td>
//                        </tr>
//                        """.formatted(x.get("name").asText(), Stream.of(x.get("remark"), x.get("nickName"), x.get("name")).map(JsonNode::asText).filter(y -> !y.isEmpty()).findFirst().orElse(null),
//                        Optional.ofNullable(null/*unreadCounts.get(x.get("name").asText())*/).map(y -> "&nbsp;" + y + "&nbsp;").orElse(""),
//                        DateFormatUtil.formatTimestamp(System.currentTimeMillis() / 1000/*x.getTime()*/))
//                )
//        );
//        html.append("</table>");
//
//        return """
//                <html lang="zh-CN">
//                  <head>
//                    <meta charset="UTF-8">
//                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
//                    <link rel="icon" href="https://cdn-icons-png.freepik.com/128/1946/1946554.png">
//                    <title>微信消息</title>
//                  </head><body>""" +
//                html +
//                "<script src=\"/page.js\"></script>" +
//                "</body></html>";
//    }

    private static String buildSessionHtml(List<Session> sessions) {
        return """
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                <link rel="icon" href="https://cdn-icons-png.freepik.com/128/1946/1946554.png">
                <title>微信消息</title>
                <style>
                    a { text-decoration: none; } li a { display: block; padding: 10px 0}
                    a > div { display: flex; justify-content: space-between;}
                    .ellipsis {white-space: nowrap; overflow: hidden; text-overflow: ellipsis;}
                    .hollow {font-size: 0.95em; color: white; text-shadow: -1px -1px 0 black, 1px -1px 0 black, -1px 1px 0 black, 1px 1px 0 black;}
                </style>
                </head><body><ul>"""
                +
                StreamSupport.stream(sessions.spliterator(), false).map(x ->
                        """
                                <li>
                                    <a href="message.html?talker=%s">
                                        <div><b>%s</b> <span>%s</span></div>
                                        <div><span class="ellipsis">%s: <span class="hollow">%s</span></span> <span>%s</span></div>
                                    </a>
                                </li>
                                """.formatted(x.userName,
                                CONTACT.get(x.userName),
                                DateFormatUtil.formatTimestamp(x.nOrder)/*x.get("nTime")*/,
                                x.nickName,
                                x.content,
                                x.nOrder * 1000 > READ_TIME.getOrDefault(x.userName, 0L) ? "🔴" : "")
                ).collect(Collectors.joining())

                + "</ul></body></html>";
    }

    private static Map<String, Long> loadWxTime() {
        try {
            return mapper.readValue(new File(WX_CFG), new TypeReference<Map<String, Long>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getIpv6() {
        final Pattern tempIpv6Pattern = Pattern.compile("临时 IPv6 地址.*? :\\s*([0-9a-fA-F:]+)");

        try (BufferedReader br = new BufferedReader(new ProcessBuilder("ipconfig", "/all").start().inputReader())) {
            return br.lines().map(tempIpv6Pattern::matcher)   // 每行生成 Matcher
                    .filter(Matcher::find)           // 只保留匹配的
                    .map(m -> m.group(1).trim())     // 提取捕获组
                    .findFirst().orElse(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void listenSendTelegram() {
        Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()).scheduleAtFixedRate(() -> {
            String ipv6 = getIpv6();
            if (ipv6.equals(IPV6)) {
                return;
            }

            String text = URLEncoder.encode("http://[" + getIpv6() + "]:8081/session", StandardCharsets.UTF_8);
            String url = "https://api.telegram.org/xxxxx/sendMessage?chat_id=-4614963368&text=" + text;

            HttpClient client = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 7897)))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            IPV6 = ipv6;
        }, 0, 10, TimeUnit.MINUTES);
    }
}
