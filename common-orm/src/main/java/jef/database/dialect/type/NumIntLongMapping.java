package jef.database.dialect.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jef.database.dialect.DatabaseDialect;
import jef.database.jdbc.result.IResultSet;

/**
 * INT <-> java.lang.Long
 * 
 * @author jiyi
 *
 */
public class NumIntLongMapping extends AbstractVersionNumberMapping {
	public Object jdbcSet(PreparedStatement st, Object value, int index, DatabaseDialect session) throws SQLException {
		if (value == null) {
			st.setNull(index, java.sql.Types.INTEGER);
		} else {
			st.setLong(index, ((Number) value).longValue());
		}
		return value;
	}
	
	@Override
	public void jdbcUpdate(ResultSet rs,String column, Object value, DatabaseDialect dialect) throws SQLException {
		rs.updateLong(column, ((Number)value).longValue());
	}

	public int getSqlType() {
		return java.sql.Types.INTEGER;
	}

	@Override
	protected String getSqlExpression(Object value, DatabaseDialect profile) {
		return value.toString();
	}

	public Object jdbcGet(IResultSet rs, int n) throws SQLException {
		Object obj = rs.getObject(n);
		if (obj == null)
			return null;
		if (obj instanceof Long)
			return obj;
		return ((Number) obj).longValue();
	}

	@Override
	protected Class<?> getDefaultJavaType() {
		return Long.class;
	}

	@Override
	Object increament(Object value) {
		if (value == null)
			return 1;
		long i = ((Number) value).longValue();
		return i + 1L;
	}

	@Override
	protected Object transfer(long n) {
		return n;
	}
}
