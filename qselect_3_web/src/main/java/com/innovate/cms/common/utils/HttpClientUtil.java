package com.innovate.cms.common.utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.google.common.collect.Lists;
import com.innovate.cms.common.config.Global;

public class HttpClientUtil
{
	private  static Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);
	
	private static final int timeOut = 10 * 1000;

	private static CloseableHttpClient httpClient = null;

	private final static Object syncLock = new Object();

	private static void config(HttpRequestBase httpRequestBase)
	{
		// 设置Header等
		// httpRequestBase.setHeader("User-Agent", "Mozilla/5.0");
		// httpRequestBase
		// .setHeader("Accept",
		// "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		// httpRequestBase.setHeader("Accept-Language",
		// "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");// "en-US,en;q=0.5");
		// httpRequestBase.setHeader("Accept-Charset",
		// "ISO-8859-1,utf-8,gbk,gb2312;q=0.7,*;q=0.7");

		// 配置请求的超时设置
		RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(timeOut).setConnectTimeout(timeOut)
				.setSocketTimeout(timeOut).build();
		httpRequestBase.setConfig(requestConfig);
	}

	/**
	 * 获取HttpClient对象
	 * 
	 * @Title: getHttpClient
	 * @Description: 获取HttpClient对象
	 * @param url
	 * @return 返回类型 CloseableHttpClient
	 *
	 */
	public static CloseableHttpClient getHttpClient(String url)
	{
		logger.debug("CloseableHttpClient getHttpClient 拿到Url=【{}】",url);
		String hostname = url.split("/")[2];
		int port = 80;
		if (hostname.contains(":"))
		{
			String[] arr = hostname.split(":");
			hostname = arr[0];
			port = Integer.parseInt(arr[1]);
		}
		if (httpClient == null)
		{
			synchronized (syncLock)
			{
				if (httpClient == null)
				{
					httpClient = createHttpClient(200, 40, 100, hostname, port);
				}
			}
		}
		return httpClient;
	}
	/**
	 * 创建HttpClient对象
	 * @Title: createHttpClient
	 * @Description: 创建HttpClient对象
	 * @param maxTotal	最大连接数
	 * @param maxPerRoute	每个路由基础的连接
	 * @param maxRoute	目标主机的最大连接数
	 * @param hostname  地址
	 * @param port	端口
	 * @return    返回类型 CloseableHttpClient
	 *
	 */
	private static CloseableHttpClient createHttpClient(int maxTotal, int maxPerRoute, int maxRoute, String hostname, int port)
	{
		ConnectionSocketFactory plainsf = PlainConnectionSocketFactory.getSocketFactory();
		LayeredConnectionSocketFactory sslsf = SSLConnectionSocketFactory.getSocketFactory();
		Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
													.register("http", plainsf)
													.register("https", sslsf).build();
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
		// 将最大连接数增加
		cm.setMaxTotal(maxTotal);
		// 将每个路由基础的连接增加
		cm.setDefaultMaxPerRoute(maxPerRoute);
		HttpHost httpHost = new HttpHost(hostname, port);
		// 将目标主机的最大连接数增加
		cm.setMaxPerRoute(new HttpRoute(httpHost), maxRoute);

		// 请求重试处理
		HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
			public boolean retryRequest(IOException exception, int executionCount, HttpContext context)
			{
				if (executionCount >= 5)
				{// 如果已经重试了5次，就放弃
					logger.debug("重试第"+executionCount+"次");
					return false;
				}
				if (exception instanceof NoHttpResponseException)
				{// 如果服务器丢掉了连接，那么就重试
					logger.debug("服务器丢失连接，正在重试...");
					return true;
				}
				if (exception instanceof SSLHandshakeException)
				{// 不要重试SSL握手异常
					logger.debug("SSL握手异常，不再重试。");
					return false;
				}
				if (exception instanceof InterruptedIOException)
				{// 超时
					logger.debug("超时，不再重试。");
					return false;
				}
				if (exception instanceof UnknownHostException)
				{// 目标服务器不可达
					logger.debug("目标服务器不可达，不再重试。");
					return false;
				}
				if (exception instanceof ConnectTimeoutException)
				{// 连接被拒绝
					logger.debug("连接被拒绝，不再重试。");
					return false;
				}
				if (exception instanceof SSLException)
				{// SSL握手异常
					logger.debug("SSL握手异常，不再重试。");
					return false;
				}

				HttpClientContext clientContext = HttpClientContext.adapt(context);
				HttpRequest request = clientContext.getRequest();
				// 如果请求是幂等的，就再次尝试
				if (!(request instanceof HttpEntityEnclosingRequest))
				{
					return true;
				}
				return false;
			}
		};

		CloseableHttpClient httpClient = HttpClients.custom()
										.setConnectionManager(cm)
										.setRetryHandler(httpRequestRetryHandler)
										.build();

		return httpClient;
	}
	
	/**
	 * 
	 * @Title: setPostParams
	 * @Description: 添加请求参数
	 * @param httppost
	 * @param params    返回类型 void
	 *
	 */
	private static void setPostParams(HttpPost httppost, Map<String, Object> params)
	{
		List<NameValuePair> nvps = Lists.newArrayList();
		Set<String> keySet = params.keySet();
		for (String key : keySet)
		{
			nvps.add(new BasicNameValuePair(key, params.get(key).toString()));
		}
		try
		{
			httppost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
		} catch (UnsupportedEncodingException e)
		{
			e.printStackTrace();
		}
	}
	/**
	 * post 请求URL获取内容
	 * @Title: post
	 * @Description: 发送 POST 请求（HTTP），K-V形式 
	 * @param url
	 * @param params
	 * @return
	 * @throws IOException    返回类型 String
	 *
	 */
	public static String doPost(String apiUrl, Map<String, Object> params)
	{
		HttpPost httpPost = new HttpPost(apiUrl);
		config(httpPost);
		setPostParams(httpPost, params);
		CloseableHttpResponse response = null;
		String result = "";
		try
		{
			response = getHttpClient(apiUrl).execute(httpPost, HttpClientContext.create());
			HttpEntity entity = response.getEntity();
            Integer statusCode = response.getStatusLine().getStatusCode(); 
            
            logger.debug("doPost --> statusCode = {}",statusCode); 
            if (statusCode != HttpStatus.OK.value())
			{
            	result = Global.getErrorJson(statusCode.toString(), HttpStatus.valueOf(statusCode).getReasonPhrase());
			}else {
				result = EntityUtils.toString(entity, "UTF-8"); 
			}
		} catch (Exception e)
		{
			result = Global.getErrorJson("-1", e.getMessage());
			e.printStackTrace();
		} finally
		{
			if (response != null) {  
                try {  
                    EntityUtils.consume(response.getEntity());  
                } catch (IOException e) {  
                	result = Global.getErrorJson("-1", e.getMessage());
                    e.printStackTrace();  
                }  
            }  
		}
		return result;
	}
	/**
	 * 
	 * @Title: doPost
	 * @Description: 发送 POST 请求（HTTP），不带输入数据 
	 * @param apiUrl
	 * @return    返回类型 String
	 *
	 */
	public static String doPost(String apiUrl) {  
        return doPost(apiUrl, new HashMap<String, Object>());  
    } 
	/**
	 * 发送 POST 请求（HTTP），JSON形式
	 * @Title: doPost
	 * @Description: 
	 * @param apiUrl
	 * @param json
	 * @return    返回类型 String
	 *
	 */
	public static String doPost(String apiUrl, Object json) {  
		HttpPost httpPost = new HttpPost(apiUrl);
		config(httpPost);
		CloseableHttpResponse response = null;
		String result = "";
		
        try {    
            StringEntity stringEntity = new StringEntity(json.toString(),"UTF-8");//解决中文乱码问题  
            stringEntity.setContentEncoding("UTF-8");  
            stringEntity.setContentType("application/json");  
            httpPost.setEntity(stringEntity);  
            response = httpClient.execute(httpPost);  
            HttpEntity entity = response.getEntity(); 
            Integer statusCode = response.getStatusLine().getStatusCode();
            
            logger.debug("doPost --> statusCode = {}",statusCode); 
            if (statusCode != HttpStatus.OK.value())
			{
            	result = Global.getErrorJson(statusCode.toString(), HttpStatus.valueOf(statusCode).getReasonPhrase());
			}else {
				result = EntityUtils.toString(entity, "UTF-8"); 
			}
        } catch (IOException e) { 
        	result = Global.getErrorJson("-1", e.getMessage());
            e.printStackTrace();  
        } finally {  
            if (response != null) {  
                try {  
                    EntityUtils.consume(response.getEntity());  
                } catch (IOException e) { 
                	result = Global.getErrorJson("-1", e.getMessage());
                    e.printStackTrace();  
                }  
            }  
        }  
        return result;  
    }  
	
	
	/**
	 * 
	 * @Title: get
	 * @Description: get 请求URL获取内容
	 * @param url
	 * @return    返回类型 String
	 *
	 */
	public static String doGet(String url)
	{
		HttpGet httpget = new HttpGet(url);
		config(httpget);
		CloseableHttpResponse response = null;
		String result = "";
		try
		{
			response = getHttpClient(url).execute(httpget, HttpClientContext.create());
			HttpEntity entity = response.getEntity();
			result = EntityUtils.toString(entity, "utf-8");
		} catch (IOException e)
		{
			result = Global.getErrorJson("-1", e.getMessage());
			e.printStackTrace();
		} finally
		{
			if (response != null)
			{
				try
				{
					EntityUtils.consume(response.getEntity());
				} catch (IOException e)
				{
					result = Global.getErrorJson("-1", e.getMessage());
					e.printStackTrace();
				}
			}
		}
		return result;
	}
	
}
