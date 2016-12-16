package jef.database.dialect.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.Date;

import jef.database.dialect.DatabaseDialect;
import jef.database.jdbc.result.IResultSet;

public class SqlTimeDateMapping extends AColumnMapping{

	public Object jdbcSet(PreparedStatement st, Object value, int index, DatabaseDialect session) throws SQLException {
		if(value==null){
			st.setNull(index, java.sql.Types.TIME);
			return value;
		}else{
			Time time=new java.sql.Time(((Date)value).getTime());
			st.setTime(index, time);
			return time;
		}
	}

	public int getSqlType() {
		return java.sql.Types.TIME;
	}

	@Override
	protected String getSqlExpression(Object value, DatabaseDialect profile) {
		if(value instanceof java.util.Date){
			return profile.getSqlTimeExpression((Date)value);
		}
		throw new IllegalArgumentException("The input param can not cast to Date.");
	}

	public Object jdbcGet(IResultSet rs, int n) throws SQLException {
		java.sql.Time obj=rs.getTime(n);
		if(obj==null)return null;
		return new Date(obj.getTime()); 
	}

	@Override
	protected Class<?> getDefaultJavaType() {
		return Date.class;
	}

	@Override
	public void jdbcUpdate(ResultSet rs, String columnIndex, Object value, DatabaseDialect dialect) throws SQLException {
		Time time=new java.sql.Time(((Date)value).getTime());
		rs.updateTime(columnIndex, time);
	}
}
