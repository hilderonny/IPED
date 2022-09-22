package iped.parsers.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.tika.metadata.Metadata;

import iped.properties.ExtraProperties;

public class LatColumnExtractor extends AbstractMetadataColumnExtractor{
	protected String[] columnNames= {"*LATITUDE", "LAT"};
	protected double min=-90;
	protected double max=90;

	@Override
	public String[] getColumnNames() {
		return columnNames;
	}

	@Override
	public boolean extractMetadata(ResultSet rs, int columnIndex) throws SQLException {
		ResultSetMetaData rsmd = rs.getMetaData();
		String colName = rsmd.getColumnName(columnIndex);
		if(isCompatibleColName(colName) && (rsmd.getColumnType(columnIndex)==Types.REAL ||rsmd.getColumnType(columnIndex)==Types.DOUBLE||rsmd.getColumnType(columnIndex)==Types.FLOAT)) {
        	Double d = rs.getDouble(columnIndex);//try to parse as double
        	if(d>=min && d<=max) {
    			extractedValues.add(rs.getString(columnIndex));
    			return true;
        	}
		}
		return false;
	}

	@Override
	public String getMetadataName() {
		return ExtraProperties.COMMON_META_PREFIX+Metadata.LATITUDE.getName();
	}
}
