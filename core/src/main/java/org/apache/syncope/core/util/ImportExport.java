/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.String;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.sql.DataSource;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.syncope.common.SyncopeConstants;
import org.apache.syncope.core.util.multiparent.MultiParentNode;
import org.apache.syncope.core.util.multiparent.MultiParentNodeOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class ImportExport extends DefaultHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ImportExport.class);

    public static final String CONTENT_FILE = "content.xml";

    private static final String ROOT_ELEMENT = "dataset";

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(SyncopeConstants.DEFAULT_DATE_PATTERN);
        }
    };

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    private final static Set<String> TABLE_PREFIXES_TO_BE_EXCLUDED =
            new HashSet<String>(Arrays.asList(new String[]{"QRTZ_", "LOGGING", "REPORTEXEC", "TASKEXEC",
                "SYNCOPEUSER", "UATTR", "UATTRVALUE", "UATTRUNIQUEVALUE", "UDERATTR", "UVIRATTR",
                "MEMBERSHIP", "MATTR", "MATTRVALUE", "MATTRUNIQUEVALUE", "MDERATTR", "MVIRATTR", "USERREQUEST"}));

    private final static Map<String, String> TABLES_TO_BE_FILTERED =
            Collections.singletonMap("TASK", "DTYPE <> 'PropagationTask'");

    private final static Map<String, Set<String>> COLUMNS_TO_BE_NULLIFIED =
            Collections.singletonMap("SYNCOPEROLE", Collections.singleton("USEROWNER_ID"));

    private String[] wfInitSQLStatements;

    private String readSchema() {
        String schema = null;

        InputStream dbPropsStream = null;
        try {
            dbPropsStream = getClass().getResourceAsStream("/persistence.properties");
            Properties dbProps = new Properties();
            dbProps.load(dbPropsStream);
            schema = dbProps.getProperty("database.schema");
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not find persistence.properties", e);
            } else {
                LOG.error("Could not find persistence.properties");
            }
        } finally {
            IOUtils.closeQuietly(dbPropsStream);
        }

        return schema;
    }

    private void setParameters(final String tableName, final Attributes attrs, final Query query) {
        Map<String, Integer> colTypes = new HashMap<String, Integer>();

        Connection conn = null;
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            conn = DataSourceUtils.getConnection(dataSource);

            final String queryString = "SELECT * FROM " + tableName;
            stmt = conn.prepareStatement(queryString);
            rs = stmt.executeQuery();
            for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
                colTypes.put(rs.getMetaData().getColumnName(i + 1).toUpperCase(),
                        rs.getMetaData().getColumnType(i + 1));
            }
        } catch (SQLException e) {
            LOG.error("While setting column types", e);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    LOG.error("While closing statement", e);
                }
            }
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    LOG.error("While closing result set", e);
                }
            }

            DataSourceUtils.releaseConnection(conn, dataSource);
            if (conn != null) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    LOG.error("While releasing connection", e);
                }
            }
        }

        for (int i = 0; i < attrs.getLength(); i++) {
            Integer colType = colTypes.get(attrs.getQName(i).toUpperCase());
            if (colType == null) {
                LOG.warn("No column type found for {}", attrs.getQName(i).toUpperCase());
                colType = Types.VARCHAR;
            }

            switch (colType) {
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                    try {
                        query.setParameter(i + 1, Integer.valueOf(attrs.getValue(i)));
                    } catch (NumberFormatException e) {
                        LOG.error("Unparsable Integer '{}'", attrs.getValue(i));
                        query.setParameter(i + 1, attrs.getValue(i));
                    }
                    break;

                case Types.NUMERIC:
                case Types.DECIMAL:
                case Types.BIGINT:
                    try {
                        query.setParameter(i + 1, Long.valueOf(attrs.getValue(i)));
                    } catch (NumberFormatException e) {
                        LOG.error("Unparsable Long '{}'", attrs.getValue(i));
                        query.setParameter(i + 1, attrs.getValue(i));
                    }
                    break;

                case Types.DOUBLE:
                    try {
                        query.setParameter(i + 1, Double.valueOf(attrs.getValue(i)));
                    } catch (NumberFormatException e) {
                        LOG.error("Unparsable Double '{}'", attrs.getValue(i));
                        query.setParameter(i + 1, attrs.getValue(i));
                    }
                    break;

                case Types.REAL:
                case Types.FLOAT:
                    try {
                        query.setParameter(i + 1, Float.valueOf(attrs.getValue(i)));
                    } catch (NumberFormatException e) {
                        LOG.error("Unparsable Float '{}'", attrs.getValue(i));
                        query.setParameter(i + 1, attrs.getValue(i));
                    }
                    break;

                case Types.DATE:
                case Types.TIME:
                case Types.TIMESTAMP:
                    try {
                        query.setParameter(i + 1, DateUtils.parseDate(
                                attrs.getValue(i), SyncopeConstants.DATE_PATTERNS), TemporalType.TIMESTAMP);
                    } catch (ParseException e) {
                        LOG.error("Unparsable Date '{}'", attrs.getValue(i));
                        query.setParameter(i + 1, attrs.getValue(i));
                    }
                    break;

                case Types.BIT:
                case Types.BOOLEAN:
                    query.setParameter(i + 1, "1".equals(attrs.getValue(i)) ? Boolean.TRUE : Boolean.FALSE);
                    break;

                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    try {
                        query.setParameter(i + 1, Hex.decode(attrs.getValue(i)));
                    } catch (IllegalArgumentException e) {
                        query.setParameter(i + 1, attrs.getValue(i));
                    }
                    break;

                case Types.BLOB:
                    try {
                        query.setParameter(i + 1, Hex.decode(attrs.getValue(i)));
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Error decoding hex string to specify a blob parameter", e);
                        query.setParameter(i + 1, attrs.getValue(i));
                    } catch (Exception e) {
                        LOG.warn("Error creating a new blob parameter", e);
                    }
                    break;

                default:
                    query.setParameter(i + 1, attrs.getValue(i));
            }
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {

        // skip root element and perform workflow init statements
        if (ROOT_ELEMENT.equals(qName)) {
            if (wfInitSQLStatements != null) {
                for (String wfInitSQLStmt : wfInitSQLStatements) {
                    Query query = entityManager.createNativeQuery(wfInitSQLStmt);
                    query.executeUpdate();
                }
            }
            return;
        }

        StringBuilder queryString = new StringBuilder("INSERT INTO ").append(qName).append('(');

        StringBuilder values = new StringBuilder();

        for (int i = 0; i < atts.getLength(); i++) {
            queryString.append(atts.getQName(i));
            values.append('?');
            if (i < atts.getLength() - 1) {
                queryString.append(',');
                values.append(',');
            }
        }
        queryString.append(") VALUES (").append(values).append(')');

        Query query = entityManager.createNativeQuery(queryString.toString());
        setParameters(qName, atts, query);

        query.executeUpdate();
    }

    private void doExportTable(final TransformerHandler handler, final Connection conn, final String tableName,
            final String whereClause) throws SQLException, SAXException {

        LOG.debug("Export table {}", tableName);

        AttributesImpl attrs = new AttributesImpl();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        ResultSet pkeyRS = null;
        try {
            // ------------------------------------
            // retrieve primary keys to perform an ordered select

            final DatabaseMetaData meta = conn.getMetaData();
            pkeyRS = meta.getPrimaryKeys(null, null, tableName);

            final StringBuilder orderBy = new StringBuilder();

            while (pkeyRS.next()) {
                final String columnName = pkeyRS.getString("COLUMN_NAME");
                if (columnName != null) {
                    if (orderBy.length() > 0) {
                        orderBy.append(",");
                    }

                    orderBy.append(columnName);
                }
            }

            // ------------------------------------
            StringBuilder query = new StringBuilder();
            query.append("SELECT * FROM ").append(tableName).append(" a");
            if (StringUtils.isNotBlank(whereClause)) {
                query.append(" WHERE ").append(whereClause);
            }
            if (orderBy.length() > 0) {
                query.append(" ORDER BY ").append(orderBy);
            }
            stmt = conn.prepareStatement(query.toString());

            rs = stmt.executeQuery();
            while (rs.next()) {
                attrs.clear();

                final ResultSetMetaData rsMeta = rs.getMetaData();
                for (int i = 0; i < rsMeta.getColumnCount(); i++) {
                    final String columnName = rsMeta.getColumnName(i + 1);
                    final Integer columnType = rsMeta.getColumnType(i + 1);

                    // Retrieve value taking care of binary values.
                    String value = getValues(rs, columnName, columnType);
                    if (value != null && (!COLUMNS_TO_BE_NULLIFIED.containsKey(tableName)
                            || !COLUMNS_TO_BE_NULLIFIED.get(tableName).contains(columnName))) {

                        attrs.addAttribute("", "", columnName, "CDATA", value);
                    }
                }

                handler.startElement("", "", tableName, attrs);
                handler.endElement("", "", tableName);

                LOG.debug("Add record {}", attrs);
            }
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    LOG.error("While closing result set", e);
                }
            }
            if (pkeyRS != null) {
                try {
                    pkeyRS.close();
                } catch (SQLException e) {
                    LOG.error("While closing result set", e);
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    LOG.error("While closing result set", e);
                }
            }
        }
    }

    private List<String> sortByForeignKeys(final Connection conn, final Set<String> tableNames, final String schema)
            throws SQLException {

        Set<MultiParentNode<String>> roots = new HashSet<MultiParentNode<String>>();

        final DatabaseMetaData meta = conn.getMetaData();

        final Map<String, MultiParentNode<String>> exploited =
                new TreeMap<String, MultiParentNode<String>>(String.CASE_INSENSITIVE_ORDER);

        final Set<String> pkTableNames = new HashSet<String>();

        for (String tableName : tableNames) {
            MultiParentNode<String> node = exploited.get(tableName);
            if (node == null) {
                node = new MultiParentNode<String>(tableName);
                roots.add(node);
                exploited.put(tableName, node);
            }

            pkTableNames.clear();

            ResultSet rs = null;
            try {
                rs = meta.getImportedKeys(conn.getCatalog(), readSchema(), tableName);

                // this is to avoid repetition
                while (rs.next()) {
                    pkTableNames.add(rs.getString("PKTABLE_NAME"));
                }
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException e) {
                        LOG.error("While closing tables result set", e);
                    }
                }
            }

            for (String pkTableName : pkTableNames) {
                if (!tableName.equalsIgnoreCase(pkTableName)) {
                    MultiParentNode<String> pkNode = exploited.get(pkTableName);
                    if (pkNode == null) {
                        pkNode = new MultiParentNode<String>(pkTableName);
                        roots.add(pkNode);
                        exploited.put(pkTableName, pkNode);
                    }

                    pkNode.addChild(node);

                    if (roots.contains(node)) {
                        roots.remove(node);
                    }
                }
            }
        }

        final List<String> sortedTableNames = new ArrayList<String>(tableNames.size());
        MultiParentNodeOp.traverseTree(roots, sortedTableNames);

        // remove from sortedTableNames any table possibly added during lookup 
        // but matching some item in this.tablePrefixesToBeExcluded
        sortedTableNames.retainAll(tableNames);

        LOG.debug("Tables after retainAll {}", sortedTableNames);

        Collections.reverse(sortedTableNames);

        return sortedTableNames;
    }

    private boolean isTableAllowed(final String tableName) {
        boolean allowed = true;
        for (String prefix : TABLE_PREFIXES_TO_BE_EXCLUDED) {
            if (tableName.toUpperCase().startsWith(prefix)) {
                allowed = false;
            }
        }
        return allowed;
    }

    public void export(final OutputStream os, final String wfTablePrefix)
            throws SAXException, TransformerConfigurationException {

        if (StringUtils.isNotBlank(wfTablePrefix)) {
            TABLE_PREFIXES_TO_BE_EXCLUDED.add(wfTablePrefix);
        }

        StreamResult streamResult = new StreamResult(os);
        final SAXTransformerFactory transformerFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();

        TransformerHandler handler = transformerFactory.newTransformerHandler();
        Transformer serializer = handler.getTransformer();
        serializer.setOutputProperty(OutputKeys.ENCODING, SyncopeConstants.DEFAULT_ENCODING);
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(streamResult);
        handler.startDocument();
        handler.startElement("", "", ROOT_ELEMENT, new AttributesImpl());

        Connection conn = null;
        ResultSet rs = null;
        try {
            conn = DataSourceUtils.getConnection(dataSource);
            final DatabaseMetaData meta = conn.getMetaData();

            final String schema = readSchema();

            rs = meta.getTables(null, schema, null, new String[]{"TABLE"});

            final Set<String> tableNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                LOG.debug("Found table {}", tableName);
                if (isTableAllowed(tableName)) {
                    tableNames.add(tableName);
                }
            }

            LOG.debug("Tables to be exported {}", tableNames);

            // then sort tables based on foreign keys and dump
            for (String tableName : sortByForeignKeys(conn, tableNames, schema)) {
                try {
                    doExportTable(handler, conn, tableName, TABLES_TO_BE_FILTERED.get(tableName.toUpperCase()));
                } catch (Exception e) {
                    LOG.error("Failure exporting table {}", tableName, e);
                }
            }
        } catch (SQLException e) {
            LOG.error("While exporting database content", e);
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    LOG.error("While closing tables result set", e);
                }
            }

            DataSourceUtils.releaseConnection(conn, dataSource);
            if (conn != null) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    LOG.error("While releasing connection", e);
                }
            }
        }

        handler.endElement("", "", ROOT_ELEMENT);
        handler.endDocument();
    }

    private String getValues(final ResultSet rs, final String columnName, final Integer columnType)
            throws SQLException {

        String res = null;

        try {
            switch (columnType) {
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    final InputStream is = rs.getBinaryStream(columnName);
                    if (is != null) {
                        res = new String(Hex.encode(IOUtils.toByteArray(is)));
                    }
                    break;

                case Types.BLOB:
                    final Blob blob = rs.getBlob(columnName);
                    if (blob != null) {
                        res = new String(Hex.encode(IOUtils.toByteArray(blob.getBinaryStream())));
                    }
                    break;

                case Types.BIT:
                case Types.BOOLEAN:
                    if (rs.getBoolean(columnName)) {
                        res = "1";
                    } else {
                        res = "0";
                    }
                    break;

                case Types.DATE:
                case Types.TIME:
                case Types.TIMESTAMP:
                    final Timestamp timestamp = rs.getTimestamp(columnName);
                    if (timestamp != null) {
                        res = DATE_FORMAT.get().format(new Date(timestamp.getTime()));
                    }
                    break;

                default:
                    res = rs.getString(columnName);
            }
        } catch (IOException e) {
            LOG.error("Error retrieving hexadecimal string", e);
        }

        return res;
    }
}
