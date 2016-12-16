package org.easyframe.tutorial.lessona;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.easyframe.tutorial.lessona.entity.Device;
import org.easyframe.tutorial.lessona.entity.Person2;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import jef.codegen.EntityEnhancer;
import jef.common.log.LogUtil;
import jef.database.DbClient;
import jef.database.DbUtils;
import jef.database.ORMConfig;
import jef.database.datasource.MapDataSourceLookup;
import jef.database.datasource.SimpleDataSource;
import jef.database.meta.MetaHolder;
import jef.database.routing.jdbc.JDataSource;
import jef.tools.DateUtils;
import jef.tools.string.RandomData;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RoutingJdbcTest {

	private static DataSource ds;

	/**
	 * 准备测试数据
	 * 
	 * @throws SQLException
	 */
	@BeforeClass
	public static void setup() throws SQLException {
		MetaHolder.getMeta(Device.class);
		MetaHolder.getMeta(Person2.class);
		ORMConfig.getInstance().setFilterAbsentTables(true);
		// 准备多个数据源
		Map<String, DataSource> datasources = new HashMap<String, DataSource>();
		// 创建三个数据库。。。
		datasources.put("datasource1", new SimpleDataSource("jdbc:derby:./db;create=true", null, null));
		datasources.put("datasource2", new SimpleDataSource("jdbc:derby:./db2;create=true", null, null));
		datasources.put("datasource3", new SimpleDataSource("jdbc:derby:./db3;create=true", null, null));
		MapDataSourceLookup lookup = new MapDataSourceLookup(datasources);
		lookup.setDefaultKey("datasource1");// 指定datasource1是默认的操作数据源
		JDataSource jds=new JDataSource(lookup);
		DbClient db=jds.getDbClient();
		ds=jds;
		db.dropTable(Device.class);
		db.createTable(Person2.class,Device.class);
		db.truncate(Person2.class);
		db.truncate(Device.class);
	}
	
	
	@Test
	public void test1() throws SQLException{
		Connection conn=ds.getConnection();
		Statement st=conn.createStatement();
		boolean flag=st.execute("insert into DeVice(indexcode,name,type,createDate) values('123456', '测试', '办公用品', current_timestamp)");
		System.out.println(flag+"  "+st.getUpdateCount());
		st.close();
	}

	
	@Test
	public void test2() throws SQLException{
		Connection conn=ds.getConnection();
		Statement st=conn.createStatement();
		boolean flag=st.execute("insert into person2(DATA_DESC,NAME,created) values('123456', '测试',current_timestamp)",1);
		ResultSet rs=st.getGeneratedKeys();
		rs.next();
		System.out.println("自增主键返回:"+rs.getInt(1));
		DbUtils.close(rs);
		System.out.println(flag+"  "+st.getUpdateCount());
		st.close();
	}
	
	@Test
	public void test3() throws SQLException{
		{
			//案例0，路由查全部
			((JDataSource)ds).getDbClient().createTable(Device.class);
			prepareData();
			executeQuery("select * from device");	
		}
		{
			//案例1，检查是否使用正确的分页规则
			System.out.println("多库多表（查全部）——使用内存分页");
			executeQuery("select * from device order by indexcode limit 12,3");
			System.out.println("单库单表——使用数据库分页");
			executeQuery("select * from device where indexcode <= '1' order by indexcode limit 12,3");
			System.out.println("单库多表——使用数据库分页");
			executeQuery("select * from device where indexcode >= '2' and indexcode<='4' order by indexcode limit 12,3");
			System.out.println("多库多表——使用内存分页");
			executeQuery("select * from device where indexcode < '4' order by indexcode limit 12,3");
			System.out.println("多库单表——使用内存分页");
			executeQuery("select * from device where indexcode <= '2' order by indexcode limit 12,3");	
		}
		{
			//案例2，垂直拆分场景
			System.out.println("垂直分库查询");
			executeQuery("select * from Person2");
			System.out.println("垂直分库——使用数据库分页");
			executeQuery("select * from Person2 order by name limit 2,12");			
		}
		{
			//补充案例0。测试无Device表的场合
			((JDataSource)ds).getDbClient().dropTable(Device.class);
			((JDataSource)ds).getDbClient().createTable(Device.class);
			executeQuery("select * from device");	
		}
	}


	private void prepareData() throws SQLException {
		List<Device> list = generateDevice(50);
		ORMConfig.getInstance().setDebugMode(false);
		((JDataSource)ds).getDbClient().batchInsert(list);
		ORMConfig.getInstance().setDebugMode(true);
	}


	private void executeQuery(String sql) throws SQLException {
		Connection conn=ds.getConnection();
		Statement st=conn.createStatement();
		boolean flag=st.execute(sql);
		if(flag){
			ResultSet rs=st.getResultSet();
			LogUtil.show(rs);
			DbUtils.close(rs);
		}
		DbUtils.close(st);
	}
	
	/*
	 * 生成一些随机的数据
	 */
	private List<Device> generateDevice(int i) {
		List<Device> result = Arrays.asList(RandomData.newArrayInstance(Device.class, i));
		String[] types = { "耗材", "大家电", "办公用品", "日用品", "电脑配件", "图书" };
		for (Device device : result) {
			device.setIndexcode(String.valueOf(RandomData.randomInteger(100000, 990000)));
			device.setCreateDate(RandomData.randomDate(DateUtils.getDate(2000, 1, 1), DateUtils.getDate(2014, 12, 31)));
			device.setType(types[RandomData.randomInteger(0, 6)]);
		}
		return result;
	}
}
