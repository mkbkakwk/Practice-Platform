package com.oj.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class MapJsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index,
                                    Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            statement.setObject(index, MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (Exception exception) {
            throw new SQLException("Unable to serialize bounded Office JSON result", exception);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private Map<String, Object> parse(String value) throws SQLException {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            throw new SQLException("Unable to parse Office JSON result", exception);
        }
    }
}
