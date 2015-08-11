package org.fiteagle.omsp.wrapper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.Preferences;

import omlBasePackage.*;
import io.github.hengyunabc.zabbix.api.DefaultZabbixApi;
import io.github.hengyunabc.zabbix.api.Request;
import io.github.hengyunabc.zabbix.api.RequestBuilder;
import io.github.hengyunabc.zabbix.api.ZabbixApi;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class Wrapper {

	static OMLBase omlclient ;
	static String zabbix_url ;
	static ZabbixApi zabbixApi ;
	
	public class SQLite {
		
	    private  String sDriver = ""; 
	    private  String sUrl = null;
	    private  int iTimeout = 30;
	    private  Connection c = null;
	    private  Statement stmt = null;
	    private  String sDriverKey = "org.sqlite.JDBC" ;
	    private  String sUrlKey;
	    
	    public SQLite(){
	        if (getPropValue("sqliteDB_path") == null)
	        {
	            sUrlKey = "jdbc:sqlite:/root/.fiteagle/monitoring_sqliteDB.db";
	        }else
	            sUrlKey = "jdbc:sqlite:"+getPropValue("sqliteDB_path");

	    	try{
			    init(sDriverKey, sUrlKey);
			    if(c != null){
			    	System.out.println("SQLite: Connected OK");
			    }
			    else{
			    	System.err.println("Connection failed");
			    }
	    	}catch(Exception e){
	    		System.err.println("Connection failed");
	    	}
	    }
	    
	    public void init(String sDriverVar, String sUrlVar) throws Exception{
	        setDriver(sDriverVar);
	        setUrl(sUrlVar);
	        setConnection();
	        setStatement();
	    }
	    
	    private void setDriver(String sDriverVar){
	        sDriver = sDriverVar;
	    }
	 
	    private void setUrl(String sUrlVar){
	        sUrl = sUrlVar;
	    }
	    
	    private  void setStatement() throws Exception {
	        if (c == null) {
	            setConnection();
	        }
	        stmt = c.createStatement();
	        stmt.setQueryTimeout(iTimeout);  // set timeout to 30 sec.
	    }
	    
	    private void setConnection() {
	    	try {
	    		Class.forName(sDriver);
	    	}catch(ClassNotFoundException ex) {
	    		ex.printStackTrace() ;
	    		System.err.println("Error: unable to load driver class");
	    		System.exit(1);
	    	}
		    try{
	    		c = DriverManager.getConnection(sUrl);
	    	}catch(SQLException ex) {
	    		System.err.println("Error: Unable to connect to database");
	    		System.exit(1);
	    	}	        	
	    }
	    
	    public ResultSet executeQry(String instruction) throws SQLException {
	        return stmt.executeQuery(instruction);
	    }
	}
	
	public Wrapper(){
		omlclient = new OMLBase(getPropValue("oml_app_name"), 
				getPropValue("oml_domain"), getPropValue("oml_sender"), 
				getPropValue("oml_collector_uri"));
		//zabbix
		zabbix_url = getPropValue("zabbix_uri") ;
		zabbixApi = new DefaultZabbixApi(zabbix_url);
        zabbixApi.init();
        boolean login = zabbixApi.login(getPropValue("zabbix_username"), getPropValue("zabbix_password"));
        System.err.println("login:" + login);
	}
	
	static private List<List<String>> fetch_data_from_sql(Wrapper wrapper){
		List<List<String>> rows = new ArrayList<List<String>>() ;
		try{
			SQLite sql = wrapper.new SQLite() ;
			ResultSet result = sql.executeQry("select distinct(host_name), collector_uri, vm_uri from virtual_physical_map") ;
			while(result.next()){
				List<String> row = new ArrayList<String>() ;
				row.add(result.getString("host_name")) ;
				row.add(result.getString("collector_uri")) ;
				row.add(result.getString("vm_uri")) ;
				rows.add(row) ;		
			}
		}catch(SQLException ex) {
			System.err.println("Can't connect to database.");
        }
		return rows ;
	}
	
	private String getPropValue(String key){
		InputStream inputStream = null ;
		try {
			Properties prop = new Properties();
			String propFileName = "config.properties";
 
			inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
 
			if (inputStream != null) {
				prop.load(inputStream);
			} else {
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			}
  
			// get the property value and print it out
			String value = prop.getProperty(key);
			inputStream.close();
			
			return value ;
		} catch (Exception e) {
			System.out.println("Exception: " + e);
		}
		return null ;
	}
	
	public static void main(String[] args) {
		System.out.println("Starting wrapper...");
		Wrapper wrapper = new Wrapper() ;
		List<List<String>> rows = fetch_data_from_sql(wrapper) ;
		System.out.println("data fetched from SQLite...");
		ArrayList<OMLMPFieldDef> mp = new ArrayList<OMLMPFieldDef>();
		mp.add(new OMLMPFieldDef("subject",OMLTypes.OML_STRING_VALUE));
		mp.add(new OMLMPFieldDef("predicate",OMLTypes.OML_STRING_VALUE));
		mp.add(new OMLMPFieldDef("object",OMLTypes.OML_STRING_VALUE));
				
		OmlMP schema = new OmlMP(mp);
		omlclient.addmp("schema", schema);
        omlclient.start();
        System.out.println("OML started...");
        while(true){        
	        for (List<String> row : rows){
		        //get host id
		        String host = row.get(0);
		        JSONObject filter = new JSONObject();
		
		        filter.put("name", new String[] { host });
		        Request getRequest = RequestBuilder.newBuilder()
		        		.method("host.get").paramEntry("filter", filter)
		        		.paramEntry("output", "extend").build();
		        JSONObject getResponse = zabbixApi.call(getRequest);
		        System.err.println(getResponse);
		        String hostid = getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("hostid");
		        System.err.println(hostid);
		
		        //get metrics
		        //total memory
		        JSONObject name = new JSONObject();	
		        name.put("name", new String[] { "Total memory" });
		        getRequest = RequestBuilder.newBuilder()
		                .method("item.get").paramEntry("filter", name)
		                .paramEntry("hostids", hostid).paramEntry("output", "extend")
		                .build();
		        getResponse = zabbixApi.call(getRequest);
		        System.err.println(getResponse);
		        float totalmemory = Float.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastvalue")) / (float) Math.pow(1000, 3) ;
		        System.err.println(totalmemory) ;
		        float totalmemory_ts = Integer.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastclock")) ;
		        System.out.println(totalmemory_ts) ;
		        
		      //used memory
		        name = new JSONObject();	
		        name.put("name", new String[] { "Used memory" });
		        getRequest = RequestBuilder.newBuilder()
		                .method("item.get").paramEntry("filter", name)
		                .paramEntry("hostids", hostid).paramEntry("output", "extend")
		                .build();
		        getResponse = zabbixApi.call(getRequest);
		        System.err.println(getResponse);
		        float usedmemory = Float.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastvalue")) / (float) Math.pow(1000, 3) ;
		        System.err.println(usedmemory) ;
		        float usedmemory_ts = Integer.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastclock")) ;
		        System.out.println(usedmemory_ts) ;
		        
		      //available memory
		        name = new JSONObject();	
		        name.put("name", new String[] { "Available memory" });
		        getRequest = RequestBuilder.newBuilder()
		                .method("item.get").paramEntry("filter", name)
		                .paramEntry("hostids", hostid).paramEntry("output", "extend")
		                .build();
		        getResponse = zabbixApi.call(getRequest);
		        System.err.println(getResponse);
		        float availablememory = Float.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastvalue")) / (float) Math.pow(1000, 3) ;
		        System.err.println(availablememory) ;
		        float availablememory_ts = Integer.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastclock")) ;
		        System.out.println(availablememory_ts) ;
		        
		      //used bandwidth
		        name = new JSONObject();	
		        name.put("key_", new String[] { "net.if.in[eth2]" });
		        getRequest = RequestBuilder.newBuilder()
		                .method("item.get").paramEntry("filter", name)
		                .paramEntry("hostids", hostid).paramEntry("output", "extend")
		                .build();
		        getResponse = zabbixApi.call(getRequest);
		        System.err.println(getResponse);
		        float usedbandwidth = Float.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastvalue")) / (float) Math.pow(1000, 3) ;
		        System.err.println(usedbandwidth) ;
		        float usedbandwidth_ts = Integer.valueOf(getResponse.getJSONArray("result")
		                .getJSONObject(0).getString("lastclock")) ;
		        System.out.println(usedbandwidth_ts) ;
		        
		        String prefix = "http://localhost/" ;
		        String rand_id = UUID.randomUUID().toString() ;
		        String resource = row.get(2) ;
		        String measurement = "<" + prefix + "measurement/" + rand_id + ">" ;
		        String metric = "<" + resource + "/UsedMemory>" ;
		        String data = "<" + prefix + "measurement_data/" + rand_id + ">" ;
		        String unit = "<" + prefix + "unit/" + rand_id + ">" ;
		        
		        List<String[]> list = new ArrayList<String[]>() ;
		        list.add(new String[]{measurement, "rdf:type", "omn-monitoring-data:SimpleMeasurement"}) ;
		        list.add(new String[]{measurement, "omn-monitoring:isMeasurementOf", metric}) ;
		        list.add(new String[]{metric, "rdfs:label", "\"UsedMemory\""}) ;
		        list.add(new String[]{metric, "rdf:type", "omn-monitoring-metric:UsedMemory"}) ;
		        list.add(new String[]{metric, "omn-monitoring:isMeasurementMetricOf", resource}) ;
		        list.add(new String[]{resource, "rdf:type", "omn-domain-pc:VM"}) ;
		        list.add(new String[]{metric, "omn-monitoring-data:hasMeasurementData", data}) ;
		        list.add(new String[]{data, "rdf:type", "omn-monitoring-data:MeasurementData"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasMeasurementDataValue", "\"" + usedmemory + "\""}) ;
		        list.add(new String[]{data, "omn-monitoring:hasUnit", unit}) ;
		        list.add(new String[]{unit, "rdf:type", "omn-monitoring-unit:Byte"}) ;
		        list.add(new String[]{unit, "omn-monitoring-unit:hasPrefix", "omn-monitoring-unit:giga"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasTimestamp", "\"" + usedmemory_ts + "\""}) ;
		        list.add(new String[]{resource, "rdfs:label", "\"" + resource + "\""}) ;
		        
		        rand_id = UUID.randomUUID().toString() ;
		        measurement = "<" + prefix + "measurement/" + rand_id + ">" ;
		        metric = "<" + resource + "/TotalMemory>" ;
		        data = "<" + prefix + "measurement_data/" + rand_id + ">" ;
		        unit = "<" + prefix + "unit/" + rand_id + ">" ;
		        
		        list.add(new String[]{measurement, "rdf:type", "omn-monitoring-data:SimpleMeasurement"}) ;
		        list.add(new String[]{measurement, "omn-monitoring:isMeasurementOf", metric}) ;
		        list.add(new String[]{metric, "rdfs:label", "\"TotalMemory\""}) ;
		        list.add(new String[]{metric, "rdf:type", "omn-monitoring-metric:TotalMemory"}) ;
		        list.add(new String[]{metric, "omn-monitoring:isMeasurementMetricOf", resource}) ;
		        list.add(new String[]{resource, "rdf:type", "omn-domain-pc:VM"}) ;
		        list.add(new String[]{metric, "omn-monitoring-data:hasMeasurementData", data}) ;
		        list.add(new String[]{data, "rdf:type", "omn-monitoring-data:MeasurementData"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasMeasurementDataValue", "\"" + totalmemory + "\""}) ;
		        list.add(new String[]{data, "omn-monitoring:hasUnit", unit}) ;
		        list.add(new String[]{unit, "rdf:type", "omn-monitoring-unit:Byte"}) ;
		        list.add(new String[]{unit, "omn-monitoring-unit:hasPrefix", "omn-monitoring-unit:giga"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasTimestamp", "\"" + totalmemory_ts + "\""}) ;
		        list.add(new String[]{resource, "rdfs:label", "\"" + resource + "\""}) ;
		        
		        rand_id = UUID.randomUUID().toString() ;
		        measurement = "<" + prefix + "measurement/" + rand_id + ">" ;
		        metric = "<" + resource + "/AvailableMemory>" ;
		        data = "<" + prefix + "measurement_data/" + rand_id + ">" ;
		        unit = "<" + prefix + "unit/" + rand_id + ">" ;
		        
		        list.add(new String[]{measurement, "rdf:type", "omn-monitoring-data:SimpleMeasurement"}) ;
		        list.add(new String[]{measurement, "omn-monitoring:isMeasurementOf", metric}) ;
		        list.add(new String[]{metric, "rdfs:label", "\"AvailableMemory\""}) ;
		        list.add(new String[]{metric, "rdf:type", "omn-monitoring-metric:AvailableMemory"}) ;
		        list.add(new String[]{metric, "omn-monitoring:isMeasurementMetricOf", resource}) ;
		        list.add(new String[]{resource, "rdf:type", "omn-domain-pc:VM"}) ;
		        list.add(new String[]{metric, "omn-monitoring-data:hasMeasurementData", data}) ;
		        list.add(new String[]{data, "rdf:type", "omn-monitoring-data:MeasurementData"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasMeasurementDataValue", "\"" + availablememory + "\""}) ;
		        list.add(new String[]{data, "omn-monitoring:hasUnit", unit}) ;
		        list.add(new String[]{unit, "rdf:type", "omn-monitoring-unit:Byte"}) ;
		        list.add(new String[]{unit, "omn-monitoring-unit:hasPrefix", "omn-monitoring-unit:giga"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasTimestamp", "\"" + availablememory_ts + "\""}) ;
		        list.add(new String[]{resource, "rdfs:label", "\"" + resource + "\""}) ;
		        
		        rand_id = UUID.randomUUID().toString() ;
		        measurement = "<" + prefix + "measurement/" + rand_id + ">" ;
		        metric = "<" + resource + "/UsedBandwidth>" ;
		        data = "<" + prefix + "measurement_data/" + rand_id + ">" ;
		        unit = "<" + prefix + "unit/" + rand_id + ">" ;
		        
		        list.add(new String[]{measurement, "rdf:type", "omn-monitoring-data:SimpleMeasurement"}) ;
		        list.add(new String[]{measurement, "omn-monitoring:isMeasurementOf", metric}) ;
		        list.add(new String[]{metric, "rdfs:label", "\"UsedBandwidth\""}) ;
		        list.add(new String[]{metric, "rdf:type", "omn-monitoring-metric:UsedBandwidth"}) ;
		        list.add(new String[]{metric, "omn-monitoring:isMeasurementMetricOf", resource}) ;
		        list.add(new String[]{resource, "rdf:type", "omn-domain-pc:VM"}) ;
		        list.add(new String[]{metric, "omn-monitoring-data:hasMeasurementData", data}) ;
		        list.add(new String[]{data, "rdf:type", "omn-monitoring-data:MeasurementData"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasMeasurementDataValue", "\"" + usedbandwidth + "\""}) ;
		        list.add(new String[]{data, "omn-monitoring:hasUnit", unit}) ;
		        list.add(new String[]{unit, "rdf:type", "omn-monitoring-unit:bitpersecond"}) ;
		        list.add(new String[]{unit, "omn-monitoring-unit:hasPrefix", "omn-monitoring-unit:mega"}) ;
		        list.add(new String[]{data, "omn-monitoring-data:hasTimestamp", "\"" + usedbandwidth_ts + "\""}) ;
		        list.add(new String[]{resource, "rdfs:label", "\"" + resource + "\""}) ;
		        
		        list.add(new String[]{"","",""}) ;
		        
		        for(int i=0;i<list.size();i++){
		        	schema.inject(list.get(i)) ;
		        }
	        }
	        try{
	        	Thread.sleep(30000); 
	        }catch(InterruptedException ex) {
	            Thread.currentThread().interrupt();
	            break ;
	        }
        }
        
        omlclient.close();

	}
	
}
