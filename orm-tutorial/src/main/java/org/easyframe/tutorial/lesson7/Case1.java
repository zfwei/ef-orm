package org.easyframe.tutorial.lesson7;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.easyframe.tutorial.lesson2.entity.Student;
import org.easyframe.tutorial.lesson4.entity.Person;
import org.easyframe.tutorial.lesson4.entity.School;
import org.easyframe.tutorial.lesson5.entity.Item;
import org.easyframe.tutorial.lesson7.entity.NodeTable;
import org.junit.BeforeClass;
import org.junit.Test;

import jef.common.wrapper.IntRange;
import jef.common.wrapper.Page;
import jef.database.DbClient;
import jef.database.DbClientBuilder;
import jef.database.NativeQuery;
import jef.database.ORMConfig;
import jef.database.Session;
import jef.database.SqlTemplate;
import jef.database.Transaction;
import jef.database.query.Func;
import jef.database.wrapper.ResultIterator;
import jef.database.wrapper.populator.Transformer;
import jef.tools.string.RandomData;

/**
 * NativeQuery相关操作演示
 * @author jiyi
 *
 */
public class Case1 extends org.junit.Assert {
	private static DbClient db;

	@BeforeClass
	public static void setup() throws SQLException {
		db = new DbClientBuilder().setEnhancePackages("org.easyframe.tutorial").build();
		
		ORMConfig.getInstance().setDebugMode(false);
		db.dropTable(NodeTable.class, Person.class, Item.class, Student.class, School.class);
		db.createTable(Person.class, Item.class, Student.class, School.class);
		db.createTable(NodeTable.class);
		Person p = new Person();
		p.setGender('F');
		p.setName("张飞");
		db.insert(p);

		p = new Person();
		p.setGender('F');
		p.setName("关羽");
		db.insert(p);

		p = new Person();
		p.setGender('F');
		p.setName("刘备");
		db.insert(p);

		Item item = new Item();
		item.setName("张飞");
		item.setCatalogyId(12);
		db.insert(item);

		Student st = new Student();
		st.setName("张飞");
		st.setDateOfBirth(new Date());
		st.setGender("F");
		st.setGrade("3");
		db.insert(st);

		st = new Student();
		st.setName("关羽");
		st.setDateOfBirth(new Date());
		st.setGender("F");
		st.setGrade("2");
		db.insert(st);
		ORMConfig.getInstance().setDebugMode(true);
	}

	/**
	 * 演示NativeQuery的两种来源，一种来自于配置，一种来自于代码中拼凑的SQL
	 */
	@Test
	public void testNativeQuery() {
		// 方法1 NamedQuery
		{
			NativeQuery<ResultWrapper> query = db.createNamedQuery("unionQuery-1", ResultWrapper.class);
			List<ResultWrapper> result = query.getResultList();
			System.out.println(result);
		}

		// 方法2 直接传入SQL
		{
			String sql = "select * from((select upper(t1.person_name) AS name, T1.gender, '1' AS GRADE," + "T2.NAME AS SCHOOLNAME	from T_PERSON T1 inner join SCHOOL T2 ON T1.CURRENT_SCHOOL_ID=T2.ID"
					+ ") union  (  select t.NAME,t.GENDER,t.GRADE,'Unknown' AS SCHOOLNAME from STUDENT t  )) a";
			NativeQuery<ResultWrapper> query = db.createNativeQuery(sql, ResultWrapper.class);
			List<ResultWrapper> result = query.getResultList();
			System.out.println(result);
		}
	}

	/**
	 * 演示NativeQuery的绑定变量参数和API用法
	 */
	@Test
	public void testQueryParams() {
		String sql = "select distinct(select grade from student s where s.name=person_name) grade,person_name,gender from t_person where id<:id";
		NativeQuery<Map> query = db.createNativeQuery(sql, Map.class);
		query.setParameter("id", 12);
		// 自动改写为count语句进行查询
		System.out.println("预计查出" + query.getResultCount() + "条结果");
		// 查询多条结果
		System.out.println(query.getResultList());

		// 重新设置参数
		System.out.println("=== 重新设置参数 ===");
		query.setParameter("id", 2);
		System.out.println("预计查出" + query.getResultCount() + "条结果");
		System.out.println(query.getResultList());
		// 查出第一条结果
		System.out.println(query.getSingleOnlyResult());
	}

	/**
	 * 一个简单的POJO，作为存放查询结果的容器
	 */
	public static class ResultWrapper {
		private String name;
		private String gender;
		private Integer grade;
		private String schoolName;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getGender() {
			return gender;
		}

		public void setGender(String gender) {
			this.gender = gender;
		}

		public Integer getGrade() {
			return grade;
		}

		public void setGrade(Integer grade) {
			this.grade = grade;
		}

		public String getSchoolName() {
			return schoolName;
		}

		public void setSchoolName(String schoolName) {
			this.schoolName = schoolName;
		}

		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}

	}

	/**
	 * 演示SChema重定向功能
	 * 
	 * SQL语句中的usera userb都是不存在的schema，通过jef.properties中的配置，被重定向到APP schema下
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testSchemaMapping() throws SQLException {
		String sql = "select * from usera.t_person union all select * from userb.t_person";
		db.createNativeQuery(sql).getResultList();
	}

	/**
	 * concat(person_name, gender) 在实际使用时会改写为 person_name||gender
	 */
	@Test
	public void testRewrite1() throws SQLException {
		String sql = "select concat(person_name, gender) from usera.t_person";
		System.out.println(db.createNativeQuery(sql).getResultList());
	}

	/**
	 * 本例演示 replace、 decode、nvl等函数在Derby上的效果
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testRewrite2() throws SQLException {
		String sql = "select replace(person_name,'张','王') person_name,decode(nvl(gender,'M'),'M','男','女') gender from t_person";
		System.out.println(db.createNativeQuery(sql).getResultList());
	}

	/**
	 * 本例演示时间日期函数被Derby方言重写后的效果
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testRewrite3() throws SQLException {
		// 获得：当前日期减去1个月，和学生生日相差的天数。
		String sql = "select datediff(add_months(sysdate, -1), DATE_OF_BIRTH),DATE_OF_BIRTH from student";
		System.out.println(db.createNativeQuery(sql).getResultList());

		// 获得：在当前日期上加上1年
		sql = "select addDate(sysdate, INTERVAL 1 YEAR)  from student";
		System.out.println(db.createNativeQuery(sql).getResultList());
	}

	/**
	 * 本例演示在非Oracle数据库上支持递归查询
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testStartWithConnectBy() throws SQLException {
		// 准备一些数据
		db.truncate(NodeTable.class);
		List<NodeTable> data = new ArrayList<NodeTable>();
		data.add(new NodeTable(1, 0, "-Root"));
		data.add(new NodeTable(2, 1, "水果"));
		data.add(new NodeTable(5, 2, " 西瓜"));
		data.add(new NodeTable(10, 2, " 葡萄"));
		data.add(new NodeTable(4, 2, " 苹果"));
		data.add(new NodeTable(8, 4, "  青涩的苹果"));
		data.add(new NodeTable(12, 4, "  我的小苹果"));

		data.add(new NodeTable(3, 1, "家电"));
		data.add(new NodeTable(6, 3, " 电视机"));
		data.add(new NodeTable(7, 3, " 洗衣机"));
		data.add(new NodeTable(11, 6, "  彩色电视机"));
		db.batchInsert(data);

		String sql = "select * from nodetable t START WITH t.id IN (4,6) CONNECT BY PRIOR t.id = t.pid";
		NativeQuery<NodeTable> query = db.createNativeQuery(sql, NodeTable.class);
		List<NodeTable> result = query.getResultList();
		for (NodeTable p : result) {
			System.out.println(p);
		}
	}

	/**
	 * 本例演示Translate函数在Derby上的效果
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testTranslate() throws SQLException {
		String sql = "select translate(person_name,'张刘关','刘关张') from t_person";
		System.out.println(db.createNativeQuery(sql).getResultList());
	}


	/**
	 * 绑定变量中使用Like条件，通过在SQL中指定参数类型使查询支持模糊匹配。
	 */
	@Test
	public void testLike() {
		String sql = "select * from t_person where person_name like :name<$string$>";
		System.out.println(db.createNativeQuery(sql).setParameter("name", "张").getResultList());
	}

	/**
	 * 动态表达式省略
	 */
	@Test
	public void testDynamicSQL() {
		// SQL语句中写了四个查询条件
		String sql = "select * from t_person where id=:id " + "and person_name like :person_name<$string$> " + "and currentSchoolId=:schoolId " + "and gender=:gender";
		NativeQuery<Person> query = db.createNativeQuery(sql, Person.class);
		{
			System.out.println("== 按ID查询 ==");
			query.setParameter("id", 1);
			Person p = query.getSingleResult();
			System.out.println(p.getId());
			System.out.println(p);
		}
		{
			System.out.println("== 由于参数'ID'并未清除，所以变为 ID + NAME查询 ==");
			query.setParameter("person_name", "张");
			System.out.println(query.getResultList());
		}
		{
			System.out.println("== 参数清除后，只传入NAME，按NAME查询 ==");
			query.clearParameters();
			query.setParameter("person_name", "张");
			System.out.println(query.getResultList());
		}
		{
			System.out.println("== 按NAME+GENDER查询 ==");
			query.setParameter("gender", "F");
			System.out.println(query.getResultList());
		}
		{
			System.out.println("== 一个参数都没有，变为查全表 ==");
			query.clearParameters();
			System.out.println(query.getResultList());
		}
	}

	/**
	 * 动态表达式省略 (IN条件)
	 */
	@Test
	public void testDynamicSQL2() {
		String sql = "select * from t_person where id not in (:ids)";
		System.out.println(db.createNativeQuery(sql, Person.class).getResultList());
	}

	/**
	 * 动态表达式省略的失效——将参数值设置为null，并不能起到清空参数的作用
	 */
	@Test
	public void testDynamicSQL3() {
		String sql = "select * from t_person where id not in (:ids)";
		NativeQuery<Person> query = db.createNativeQuery(sql, Person.class);
		// 将参数值设置为null，并不能起到清空参数的作用
		query.setParameter("ids", null);
		System.out.println(query.getResultList());
	}

	/**
	 * 动态表达式省略——不仅仅是where条件可以省略，update中的赋值表达式也可以省略
	 */
	@Test
	public void testDynamicSQL4() {
		String sql = "update t_person set person_name=:new_name, current_school_id=:new_schoold_id,gender=:new_gender where id=:id";
		NativeQuery<Person> query = db.createNativeQuery(sql, Person.class);
		query.setParameter("new_name", "孟德");
		query.setParameter("id", 1);
		int count = query.executeUpdate();
		System.out.println(count);
	}

	/**
	 * 动态SQL片段支持
	 */
	@Test
	public void testDynamicSqlExpression() {
		String sql = "select :columns<sql> from t_person where " + "id in (:ids<int>) and person_name like :person_name<$string$> " + "order by :orders<sql>";

		NativeQuery<Person> query = db.createNativeQuery(sql, Person.class);
		// 查询哪些列、按什么列排序，都是在查询创建以后动态指定的。
		query.setParameter("columns", "id, person_name, gender");
		query.setParameter("orders", "gender asc");

		System.out.println(query.getResultList());

		// 动态SQL片段和动态表达式省略功能混合使用
		query.setParameter("ids", new int[] { 1, 2, 3 });
		query.setParameter("columns", "person_name, id + 1000 as id");
		System.out.println(query.getResultList());
	}

	@Test
	public void testNativeQueryPage() throws SQLException {
		// SQL语句中写了四个查询条件
		String sql = "select * from t_person where id=:id " + "and person_name like :person_name<$string$> " + "and currentSchoolId=:schoolId " + "and gender=:gender";
		NativeQuery<Person> query = db.createNativeQuery(sql, Person.class);
		query.setParameter("gender", 'F');

		// 每页5条，跳过最前面的2条记录
		Page<Person> page = db.pageSelect(query, 5).setOffset(2).getPageData();
		System.out.println("总共:" + page.getTotalCount() + " " + page.getList());
	}

	/**
	 * 在命名查询中为不同数据库分别配置了SQL的场景下，会自动选择适合当前数据库的SQL进行操作
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testNativeQueryPage2() throws SQLException {
		NativeQuery<Person> query = db.createNamedQuery("testOracleTree");// #oracle
		if (query.containsParam("value")) { // 检查SQL是否需要该参数
			query.setParameter("value", 100);
		}
		System.out.println(query.getResultList());
	}

	/**
	 * 使用原生SQL语句进行查询
	 * 
	 * @throws SQLException
	 */
	@Test
	public void testRawSQL() throws SQLException {
		// 普通的原生SQL查询
		String sql = "select id, person_name,gender from t_person";
		{
			List<Person> result = db.selectBySql(sql, Person.class);
			System.out.println(result);
			assertEquals(3, result.size());
		}
		// 限定结果范围——分页
		{
			List<Person> result = db.selectBySql(sql, new Transformer(Person.class), new IntRange(2, 3));
			System.out.println(result);
			assertEquals(2, result.size());
		}
		// 使用绑定变量
		{
			sql = "select * from t_person where person_name like ? or gender=?";
			List<Person> result = db.selectBySql(sql, Person.class, "刘", "F");
			System.out.println(result);
			assertEquals(3, result.size());
		}
		// 执行原生SQL
		{
			sql = "insert into t_person(person_name,gender,created) values(?,?,current_timestamp)";
			db.executeSql(sql, "曹操", "M");
			db.executeSql(sql, "郭嘉", "M");
			assertEquals(5, db.getSqlTemplate(null).countBySql("select count(*) from t_person"));
		}
	}

	/**
	 * 无表查询
	 */
	@Test
	public void testExpressionValue() throws SQLException {
		// 传入复杂表达式时，其函数和语法会被改写
		String s = "'今天是'||str(cast(year(sysdate)/100+1 as int))||'世纪'";
		assertEquals("今天是21世纪", db.getExpressionValue(s, String.class));

		// 要在某个特定数据库上执行无表查询，可以用SqlTemplate
		SqlTemplate t = db.getSqlTemplate(null);
		// 直接传入数据库函数
		Date dbTime = t.getExpressionValue(Func.current_timestamp, Date.class);
		System.out.println("当前时间为:" + dbTime);
	}

	/**
	 * 补充测试，支持limit关键字改写为当前数据库的分页
	 */
	@Test
	public void testLimitKeyword() throws SQLException {
		Transaction db=this.db.startTransaction();
		insertMore(10,db);
		String sql = "select id, person_name,gender from t_person order by person_name limit 8 offset 4"; 
		{
			NativeQuery<Person> query=db.createNativeQuery(sql, Person.class);
			assertEquals(8, query.getResultCount());
			assertEquals(8, query.getResultList().size());
			ResultIterator<Person> it = db.createNativeQuery(sql, Person.class).getResultIterator();
			int count=0;
			for (; it.hasNext();) {
				it.next();
				count++;
			}
			it.close();
			assertEquals(8, count);
		}
		{
			System.out.println("=========== Normal");
			NativeQuery<Person> query = db.createNativeQuery("select id, person_name,gender from t_person order by person_name", Person.class);
			query.setRange(new IntRange(6, 10));
			assertEquals(13, query.getResultCount());
			List<Person> list=query.getResultList();
			assertEquals(5, list.size());
			//特性：Range设定不会影响getResultSet()方法, API文档已修订
			
		}
		{
			
			//案例3，当limit和range同时指定后，range使用数据库分页，limit被SQL上移除，但会补做一次内存分页。(需要用NativeQuery测试)
			System.out.println("=========== add range 1,2");
			System.out.println(sql);
			NativeQuery<Person> query = db.createNativeQuery(sql, Person.class);
			assertEquals(8, query.getResultCount());
			System.out.println("Range set");
			query.setRange(new IntRange(3, 3));
			assertEquals(8, query.getResultCount());	//设置Range，不影响结果
			
			
			System.out.println("进行了双重过滤，原先的SQL语句过滤了前4条，这里又过滤了两条，相当于过滤了7条。");
			assertEquals(1, query.getResultList().size());
		}
		db.rollback();
		db.close();
	}

	private void insertMore(int max,Session session) throws SQLException {
		List<Person> ps=new ArrayList<Person>();
		for(int i=0;i<max;i++){
			Person p = new Person();
			p.setGender('M');
			p.setCurrentSchoolId(1);
			p.setCreated(new Date());
			p.setName(RandomData.randomChineseName());	
			ps.add(p);
		}
		session.batchInsert(ps);
	}
}
