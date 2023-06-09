package com.ll.query;

import com.ll.function.ThrowingFunction;
import lombok.AllArgsConstructor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor
public enum Query {
    SHOW(Query::getMappedResult),
    DESC(Query::getMappedResult),
    SELECT(Query::getMappedResult),
    INSERT(ps -> {
        ps.executeUpdate();
        ResultSet generatedKeys = ps.getGeneratedKeys();
        if (generatedKeys.next()) {
            long key = generatedKeys.getLong(1);
            generatedKeys.close();
            return key;
        }
        generatedKeys.close();
        return null;
    }),
    UPDATE(PreparedStatement::executeUpdate),
    DELETE(PreparedStatement::executeUpdate);

    private static List<Map<String, Object>> getMappedResult(PreparedStatement ps) throws SQLException {
        List<Map<String, Object>> datum = new ArrayList<>();
        ResultSet resultSet = ps.executeQuery();
        mapResult(resultSet, datum);
        resultSet.close();
        return datum;
    }

    private ThrowingFunction<PreparedStatement, Object, SQLException> method;

    public static <T> T execute(PreparedStatement ps, String queryType) throws SQLException {
        try {
            Query query = Query.valueOf(queryType);
            return (T) query.method.apply(ps);
        } catch (IllegalArgumentException e) {
            return (T)(Boolean)ps.execute();
        }
    }

    public static String getQueryType(String query) {
        Pattern pattern = Pattern.compile("^\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        throw new IllegalArgumentException("Invalid query format");
    }

    private static void mapResult(ResultSet resultSet, List<Map<String, Object>> datum) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int colLen = metaData.getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> data = new HashMap<>();
            mapColumn(resultSet, metaData, colLen, data);
            datum.add(data);
        }
    }

    private static void mapColumn(ResultSet resultSet, ResultSetMetaData metaData, int colLen, Map<String, Object> data)
            throws SQLException {
        for (int i = 1; i <= colLen; i++) {
            String columnName = metaData.getColumnName(i);
            Object content = resultSet.getObject(i);
            data.put(columnName, content);
        }
    }
}
