package brightspot.tutorial.article;

import brightspot.tutorial.image.Image;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psddev.cms.rte.RichTextViewBuilder;
import com.psddev.cms.view.ViewInterface;
import com.psddev.cms.view.ViewModel;
import com.psddev.dari.util.*;
import com.psddev.dari.util.gson.JsonObject;
import com.psddev.handlebars.HandlebarsTemplate;
import org.apache.commons.io.FileUtils;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

import javax.security.auth.login.FailedLoginException;
import java.io.*;
import java.net.URL;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.*;

@ViewInterface
@HandlebarsTemplate("brightspot/tutorial/article/Article")
public class ArticleViewModel extends ViewModel<Article> {

    private String zabbixRestAuth;

    public void setZabbixRestAuth(String zabbixRestAuth) {
        this.zabbixRestAuth = zabbixRestAuth;
    }

    public String getZabbixRestAuth() {
        return zabbixRestAuth;
    }

    public String getHeadline() {
        return model.getHeadline()+"something";
    }

    public String getLeadImageUrl() {
        Image leadImage = model.getLeadImage();
        if (leadImage != null) {
            StorageItem file = leadImage.getFile();
            if (file != null) {
                return file.getPublicUrl();
            }
        }
        return null;
    }

    public CharSequence getBody() {
        try {
            test2();
        } catch (Exception ex) {

        }
        String body = model.getBody();
        if (body != null) {
            return RichTextViewBuilder.buildHtml(body, (rte) -> null);
        }
        return null;
    }

    private void test() throws UnsupportedEncodingException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://web.zabbix.psdops.com/index.php");

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("name", "zabbix_api"));
        params.add(new BasicNameValuePair("password", "esef4cgc23fh0acj6u2e0hj2s7amo637"));
        params.add(new BasicNameValuePair("enter", "Sign in"));
        httpPost.setEntity(new UrlEncodedFormEntity(params));

        try {
            CloseableHttpResponse response = client.execute(httpPost);
            response.getHeaders("Cookie");

            //HttpGet httpGet = new HttpGet("https://web.zabbix.psdops.com/chart2.php?graphid=29312&period=3600&stime=20180912130938&isNow=0");
            HttpGet httpGet = new HttpGet("https://web.zabbix.psdops.com/chart2.php?graphid=11371&period=3600&stime=20180919150234&isNow=0");
            httpGet.addHeader(response.getFirstHeader("Set-Cookie"));
            response = client.execute(httpGet);

            Image img = new Image();
            img.setTitle("Lets see if it works 7");

            InputStream is = response.getEntity().getContent();

            byte[] bytes = IoUtils.toByteArray(is);

            StorageItem newStorageItem = StorageItem.Static.create();
            try {
                newStorageItem.setData(new ByteArrayInputStream(bytes));
                String name = "name of image";
                String suffix = "png";
                newStorageItem.setPath(new RandomUuidStorageItemPathGenerator().createPath("name of image") + "/"
                        + (StringUtils.isBlank(suffix) ? name : (name + "." + suffix)));
                newStorageItem.setContentType(response.getEntity().getContentType().getValue());

                Map<String, Object> newMetadata = newStorageItem.getMetadata();
                newMetadata.putAll(new ImageMetadataMap(new ByteArrayInputStream(bytes)));
                newMetadata.put("brightcove.ingestedDate", new Date());
                newStorageItem.save();
                img.setFile(newStorageItem);
                img.save();

            } catch (Exception ex) {
                System.out.print(ex.getSuppressed());
            }

            client.close();

            /////
        } catch (Exception ex) {

        }
    }


    private void test2() {
        try {
            Map<String, StorageItem> zabbixGraphMap = getZabbixGraphs("i-0eac5307d295d96b2");
            for (String key: zabbixGraphMap.keySet()) {
                Image img = new Image();
                img.setTitle(key);
                img.setFile(zabbixGraphMap.get(key));
                img.save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loginZabbixRest(String user, String password) throws IOException, FailedLoginException, JSONException {
        ZabbixRequestLogin zabbixRequestLogin = new ZabbixRequestLogin(user,password);
        zabbixRequestLogin.setCommonParam(1,"2.0","user.login");
        JSONObject jsonObject = getJsonResource(zabbixRequestLogin);

        setZabbixRestAuth(jsonObject.has("result") ? jsonObject.getString("result") : null);

        if (getZabbixRestAuth() == null) {
            throw new FailedLoginException("Zabbix Login Failure for user - " + user);
        }
    }

    private String getHostIdZabbix(String hostName) throws IOException, JSONException {
        String hostId = null;
        ZabbixRequestHost zabbixRequestHost = new ZabbixRequestHost(getZabbixRestAuth(),hostName);
        zabbixRequestHost.setCommonParam(1,"2.0","host.get");
        JSONObject hostResponseObject = getJsonResource(zabbixRequestHost);

        JSONArray hostResponseResultArray = hostResponseObject.has("result") ? hostResponseObject.getJSONArray("result") : null;

        if (hostResponseResultArray != null) {
            JSONObject hostResponseResultObject = hostResponseResultArray.getJSONObject(0);
            hostId =  hostResponseResultObject.has("hostid") ? hostResponseResultObject.getString("hostid") : null;
        }

        if (hostId == null) {
            throw new InvalidParameterException("No hostId found for hostname - " + hostName);
        }

        return  hostId;
    }

    private Map<String, String> getGraphIdZabbix(int hostId, List<String> graphNameWildCardList) throws IOException, JSONException {
        ZabbixRequestGraph zabbixRequestGraph = new ZabbixRequestGraph(getZabbixRestAuth(), "extend", hostId, graphNameWildCardList);
        zabbixRequestGraph.setCommonParam(1,"2.0","graph.get");
        JSONObject graphResponseObject = getJsonResource(zabbixRequestGraph);

        JSONArray graphResponseResultArray = graphResponseObject.has("result") ? graphResponseObject.getJSONArray("result") : null;

        Map<String, String> graphMapList = new HashMap<>();

        String timestamp = new Timestamp(System.currentTimeMillis()).getTime() + "";

        if (graphResponseResultArray != null) {
            for (int i = 0; i < graphResponseResultArray.length(); i++) {
                JSONObject graphResponseResultObject = graphResponseResultArray.getJSONObject(i);
                if (graphResponseResultObject.has("graphid")) {
                    graphMapList.put(graphResponseResultObject.getString("graphid"),graphResponseResultObject.getString("name") + "_" + timestamp);
                }
            }
        }

        return  graphMapList;
    }

    public JSONObject getJsonResource(Object zabbixRequest) throws IOException, JSONException {
        ObjectMapper mapper = new ObjectMapper();
        String zabbixRequestObj = mapper.writeValueAsString(zabbixRequest);

        CloseableHttpClient client = HttpClientBuilder.create().build();
        URL url = new URL("https://web.zabbix.psdops.com/api_jsonrpc.php");
        HttpPost post = new HttpPost(url.toString());

        post.setConfig(RequestConfig.custom().setConnectionRequestTimeout(20000).setSocketTimeout(20000).build());

        StringEntity stringEntity = new StringEntity(zabbixRequestObj);

        post.setEntity(stringEntity);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");

        HttpResponse response = client.execute(post);

        JSONObject jsonObject = new JSONObject(EntityUtils.toString(response.getEntity()));
        client.close();
        return jsonObject;

    }

    abstract class ZabbixRequest {
        private String jsonrpc;
        private String method;
        private int id;

        public String getJsonrpc() {
            return jsonrpc;
        }

        public String getMethod() {
            return method;
        }

        public int getId() {
            return id;
        }

        public void setCommonParam(int id, String jsonrpc, String method) {
            this.id = id;
            this.jsonrpc = jsonrpc;
            this.method = method;
        }
    }

    abstract class ZabbixRequestAuthenticated extends ZabbixRequest {
        private String auth;

        public String getAuth() {
            return auth;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }
    }

    class ZabbixRequestLogin extends ZabbixRequest{
        private ZabbixRequestLoginParams params;

        public ZabbixRequestLoginParams getParams() {
            return params;
        }

        ZabbixRequestLogin(String user, String password) {
            params = new ZabbixRequestLoginParams();
            params.setUser(user);
            params.setPassword(password);
        }

        class ZabbixRequestLoginParams {
            private String user;

            private String password;

            public String getUser() {
                return user;
            }

            public void setUser(String user) {
                this.user = user;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String password) {
                this.password = password;
            }
        }
    }

    class ZabbixRequestHost extends ZabbixRequestAuthenticated {
        private ZabbixRequestHostParams params;

        public ZabbixRequestHostParams getParams() {
            return params;
        }

        ZabbixRequestHost(String auth,String hostName) {
            setAuth(auth);
            params = new ZabbixRequestHostParams(hostName);
        }

        class ZabbixRequestHostParams {
            private ZabbixRequestHostParamsFilter filter;

            public ZabbixRequestHostParamsFilter getFilter() {
                return filter;
            }

            ZabbixRequestHostParams(String hostName) {
                filter = new ZabbixRequestHostParamsFilter(hostName);
            }

            class ZabbixRequestHostParamsFilter {
                private List<String> host;

                public List<String> getHost() {
                    return host;
                }

                ZabbixRequestHostParamsFilter(String hostName) {
                    host = new ArrayList<>();
                    host.add(hostName);
                }
            }
        }
    }

    class ZabbixRequestGraph extends ZabbixRequestAuthenticated {
        private ZabbixRequestGraphParams params;

        public ZabbixRequestGraphParams getParams() {
            return params;
        }

        ZabbixRequestGraph(String auth, String output, int hostId, List<String> name) {
            setAuth(auth);
            this.params = new ZabbixRequestGraphParams(output, hostId, name);
        }

        class ZabbixRequestGraphParams {
            private String output;
            private int hostids;
            private ZabbixRequestGraphParamSearch search;
            private boolean searchWildcardsEnabled = true;

            public String getOutput() {
                return output;
            }

            public int getHostids() {
                return hostids;
            }

            public ZabbixRequestGraphParamSearch getSearch() {
                return search;
            }

            public boolean getSearchWildcardsEnabled() {
                return searchWildcardsEnabled;
            }

            ZabbixRequestGraphParams(String output, int hostId, List<String> name) {
                this.output = output;
                this.hostids = hostId;
                this.search = new ZabbixRequestGraphParamSearch(name);
            }

            class ZabbixRequestGraphParamSearch {
                private List<String> name;

                public List<String> getName() {
                    return name;
                }

                ZabbixRequestGraphParamSearch(List<String> name) {
                    this.name = name;
                }
            }
        }
    }

    private Map<String, StorageItem> getZabbixGraphs(String hostName) throws Exception{

        loginZabbixRest("zabbix_api","esef4cgc23fh0acj6u2e0hj2s7amo637");

        String hostIdStr = getHostIdZabbix(hostName);

        int hostId = Integer.parseInt(hostIdStr);

        List<String> graphNameWildCardList = new ArrayList<>();
        graphNameWildCardList.add("*CPU*");

        Map<String, String> graphIdList = getGraphIdZabbix(hostId, graphNameWildCardList);

        CloseableHttpClient client = LoginZabbixHttp("zabbix_api","esef4cgc23fh0acj6u2e0hj2s7amo637");

        Map<String, StorageItem> storageItemMap = new HashMap<>();

        for (String key: graphIdList.keySet()) {
            storageItemMap.put(graphIdList.get(key), getStorageItemGraph(client, key, graphIdList.get(key)));
        }

        client.close();

        return storageItemMap;
    }

    private CloseableHttpClient LoginZabbixHttp(String user, String password) throws Exception{
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://web.zabbix.psdops.com/index.php");

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("name", user));
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("enter", "Sign in"));
        httpPost.setEntity(new UrlEncodedFormEntity(params));

        CloseableHttpResponse response = client.execute(httpPost);

        Header firstHeader = response.getFirstHeader("Set-Cookie");
        String name = firstHeader.getName();
        String value = firstHeader.getValue();
        HeaderElement[] elements = firstHeader.getElements();
        ttt header = new ttt(name, value, elements);

        return client;
    }

    private StorageItem getStorageItemGraph(CloseableHttpClient client, String graphId, String graphName) throws Exception {
        HttpGet httpGet = new HttpGet("https://web.zabbix.psdops.com/chart2.php?graphid=" + graphId + "&period=3600&stime=20180919150234&isNow=0");
        CloseableHttpResponse response = client.execute(httpGet);
        InputStream is = response.getEntity().getContent();

        byte[] bytes = IoUtils.toByteArray(is);

        StorageItem newStorageItem = StorageItem.Static.create();

        newStorageItem.setData(new ByteArrayInputStream(bytes));
        String suffix = "png";
        newStorageItem.setPath(new RandomUuidStorageItemPathGenerator().createPath(graphName) + "/" + graphName + "." + suffix);
        newStorageItem.setContentType(response.getEntity().getContentType().getValue());

        Map<String, Object> newMetadata = newStorageItem.getMetadata();
        newMetadata.putAll(new ImageMetadataMap(new ByteArrayInputStream(bytes)));
        newMetadata.put("brightcove.ingestedDate", new Date());
        newStorageItem.save();

        return newStorageItem;
    }

    class ttt implements Header {

        private String name;

        private String value;

        private HeaderElement[] elements;

        ttt(String name, String value, HeaderElement[] elements) {
            this.name = name;
            this.value = value;
            this.elements = elements;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public HeaderElement[] getElements() throws ParseException {
            return elements;
        }
    }

}
