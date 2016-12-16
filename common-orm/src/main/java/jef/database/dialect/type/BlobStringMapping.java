package jef.database.dialect.type;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import jef.database.ORMConfig;
import jef.database.dialect.DatabaseDialect;
import jef.database.jdbc.result.IResultSet;
import jef.tools.IOUtils;

public class BlobStringMapping extends AColumnMapping {

	public Object jdbcSet(PreparedStatement st, Object value, int index, DatabaseDialect session) throws SQLException {
		if (value == null) {
			st.setNull(index, session.getImplementationSqlType(Types.BLOB));
		} else {
			byte[] buf = ((String) value).getBytes(ORMConfig.getInstance().getDbEncodingCharset());
			st.setBytes(index, buf);
		}
		return value;
	}

	public int getSqlType() {
		return Types.BLOB;
	}

	@Override
	protected String getSqlExpression(Object value, DatabaseDialect profile) {
		throw new UnsupportedOperationException();
	}

	public boolean isLob() {
		return true;
	}

	public static int getLength(String str) {
		return ORMConfig.getInstance().getDbEncodingCharset().encode(str).limit();
	}

	public Object jdbcGet(IResultSet rs, int n) throws SQLException {
		Object obj = rs.getObject(n);
		if (obj == null)
			return null;
		if (obj.getClass().isArray()) {
			byte[] data = (byte[]) obj;
			return new String(data, 0, data.length, ORMConfig.getInstance().getDbEncodingCharset());
		}
		Blob blob = (Blob) obj;
		InputStream in = blob.getBinaryStream();
		try {
			return IOUtils.asString(in, ORMConfig.getInstance().getDbEncoding(), true);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	protected Class<?> getDefaultJavaType() {
		return String.class;
	}

	@Override
	public void jdbcUpdate(ResultSet rs, String columnName, Object value, DatabaseDialect dialect) throws SQLException {
		byte[] buf = ((String) value).getBytes(ORMConfig.getInstance().getDbEncodingCharset());
		rs.updateBytes(columnName, buf);
	}
}
