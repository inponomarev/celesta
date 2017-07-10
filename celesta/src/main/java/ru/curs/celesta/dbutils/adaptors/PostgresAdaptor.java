/*
   (с) 2013 ООО "КУРС-ИТ"  

   Этот файл — часть КУРС:Celesta.
   
   КУРС:Celesta — свободная программа: вы можете перераспространять ее и/или изменять
   ее на условиях Стандартной общественной лицензии GNU в том виде, в каком
   она была опубликована Фондом свободного программного обеспечения; либо
   версии 3 лицензии, либо (по вашему выбору) любой более поздней версии.

   Эта программа распространяется в надежде, что она будет полезной,
   но БЕЗО ВСЯКИХ ГАРАНТИЙ; даже без неявной гарантии ТОВАРНОГО ВИДА
   или ПРИГОДНОСТИ ДЛЯ ОПРЕДЕЛЕННЫХ ЦЕЛЕЙ. Подробнее см. в Стандартной
   общественной лицензии GNU.

   Вы должны были получить копию Стандартной общественной лицензии GNU
   вместе с этой программой. Если это не так, см. http://www.gnu.org/licenses/.

   
   Copyright 2013, COURSE-IT Ltd.

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see http://www.gnu.org/licenses/.

 */

package ru.curs.celesta.dbutils.adaptors;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import ru.curs.celesta.CelestaException;
import ru.curs.celesta.dbutils.meta.DBColumnInfo;
import ru.curs.celesta.dbutils.meta.DBFKInfo;
import ru.curs.celesta.dbutils.meta.DBIndexInfo;
import ru.curs.celesta.dbutils.meta.DBPKInfo;
import ru.curs.celesta.dbutils.stmt.ParameterSetter;
import ru.curs.celesta.event.TriggerQuery;
import ru.curs.celesta.score.*;

/**
 * Адаптер Postgres.
 */
final class PostgresAdaptor extends OpenSourceDbAdaptor {

  private static final Pattern HEX_STRING = Pattern.compile("'\\\\x([0-9A-Fa-f]+)'");

  static {
    TYPES_DICT.put(IntegerColumn.class, new ColumnDefiner() {
      @Override
      String dbFieldType() {
        return "int4";
      }

      @Override
      String getMainDefinition(Column c) {
        return join(c.getQuotedName(), dbFieldType(), nullable(c));
      }

      @Override
      String getDefaultDefinition(Column c) {
        IntegerColumn ic = (IntegerColumn) c;
        String defaultStr = "";
        if (ic.getDefaultValue() != null) {
          defaultStr = DEFAULT + ic.getDefaultValue();
        }
        return defaultStr;
      }
    });

    TYPES_DICT.put(FloatingColumn.class, new ColumnDefiner() {

      @Override
      String dbFieldType() {
        return "float8"; // double precision";
      }

      @Override
      String getMainDefinition(Column c) {
        return join(c.getQuotedName(), dbFieldType(), nullable(c));
      }

      @Override
      String getDefaultDefinition(Column c) {
        FloatingColumn ic = (FloatingColumn) c;
        String defaultStr = "";
        if (ic.getDefaultValue() != null) {
          defaultStr = DEFAULT + ic.getDefaultValue();
        }
        return defaultStr;
      }
    });

    TYPES_DICT.put(BooleanColumn.class, new ColumnDefiner() {

      @Override
      String dbFieldType() {
        return "bool";
      }

      @Override
      String getMainDefinition(Column c) {
        return join(c.getQuotedName(), dbFieldType(), nullable(c));
      }

      @Override
      String getDefaultDefinition(Column c) {
        BooleanColumn ic = (BooleanColumn) c;
        String defaultStr = "";
        if (ic.getDefaultValue() != null) {
          defaultStr = DEFAULT + "'" + ic.getDefaultValue() + "'";
        }
        return defaultStr;
      }
    });

    TYPES_DICT.put(StringColumn.class, new ColumnDefiner() {

      @Override
      String dbFieldType() {
        return "varchar";
      }

      @Override
      String getMainDefinition(Column c) {
        StringColumn ic = (StringColumn) c;
        String fieldType = ic.isMax() ? "text" : String.format("%s(%s)", dbFieldType(), ic.getLength());
        return join(c.getQuotedName(), fieldType, nullable(c));
      }

      @Override
      String getDefaultDefinition(Column c) {
        StringColumn ic = (StringColumn) c;
        String defaultStr = "";
        if (ic.getDefaultValue() != null) {
          defaultStr = DEFAULT + StringColumn.quoteString(ic.getDefaultValue());
        }
        return defaultStr;
      }
    });

    TYPES_DICT.put(BinaryColumn.class, new ColumnDefiner() {

      @Override
      String dbFieldType() {
        return "bytea";
      }

      @Override
      String getMainDefinition(Column c) {
        return join(c.getQuotedName(), dbFieldType(), nullable(c));
      }

      @Override
      String getDefaultDefinition(Column c) {
        BinaryColumn bc = (BinaryColumn) c;
        String defaultStr = "";
        if (bc.getDefaultValue() != null) {
          Matcher m = HEXSTR.matcher(bc.getDefaultValue());
          m.matches();
          defaultStr = DEFAULT + String.format("E'\\\\x%s'", m.group(1));
        }
        return defaultStr;
      }
    });

    TYPES_DICT.put(DateTimeColumn.class, new ColumnDefiner() {

      @Override
      String dbFieldType() {
        return "timestamp";
      }

      @Override
      String getMainDefinition(Column c) {
        return join(c.getQuotedName(), dbFieldType(), nullable(c));
      }

      @Override
      String getDefaultDefinition(Column c) {
        DateTimeColumn ic = (DateTimeColumn) c;
        String defaultStr = "";
        if (ic.isGetdate()) {
          defaultStr = DEFAULT + NOW;
        } else if (ic.getDefaultValue() != null) {
          DateFormat df = new SimpleDateFormat("yyyyMMdd");
          defaultStr = String.format(DEFAULT + " '%s'", df.format(ic.getDefaultValue()));

        }
        return defaultStr;
      }
    });
  }

  @Override
  boolean userTablesExist(Connection conn) throws SQLException {
    PreparedStatement check = conn.prepareStatement("select count(*) from information_schema.tables "
        + "where table_type = 'BASE TABLE' " + "and table_schema not in ('pg_catalog', 'information_schema');");
    ResultSet rs = check.executeQuery();
    try {
      rs.next();
      return rs.getInt(1) != 0;
    } finally {
      rs.close();
      check.close();
    }
  }

  @Override
  public int getCurrentIdent(Connection conn, Table t) throws CelestaException {
    String sql = String.format("select last_value from \"%s\".\"%s_seq\"", t.getGrain().getName(), t.getName());
    try {
      Statement stmt = conn.createStatement();
      try {
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        return rs.getInt(1);
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException(e.getMessage());
    }
  }

  @Override
  public PreparedStatement getInsertRecordStatement(Connection conn, Table t, boolean[] nullsMask,
                                                    List<ParameterSetter> program) throws CelestaException {

    Iterator<String> columns = t.getColumns().keySet().iterator();
    // Создаём параметризуемую часть запроса, пропуская нулевые значения.
    StringBuilder fields = new StringBuilder();
    StringBuilder params = new StringBuilder();
    for (int i = 0; i < t.getColumns().size(); i++) {
      String c = columns.next();
      if (nullsMask[i])
        continue;
      if (params.length() > 0) {
        fields.append(", ");
        params.append(", ");
      }
      params.append("?");
      fields.append('"');
      fields.append(c);
      fields.append('"');
      program.add(ParameterSetter.create(i));
    }

    String returning = "";
    for (Column c : t.getColumns().values())
      if (c instanceof IntegerColumn && ((IntegerColumn) c).isIdentity()) {
        returning = " returning " + c.getQuotedName();
        break;
      }

    final String sql;

    if (fields.length() == 0 && params.length() == 0) {
      sql = String.format("insert into " + tableTemplate() + " default values %s;", t.getGrain().getName(),
          t.getName(), returning);
    } else {
      sql = String.format("insert into " + tableTemplate() + " (%s) values (%s)%s;", t.getGrain().getName(),
          t.getName(), fields.toString(), params.toString(), returning);
    }

    return prepareStatement(conn, sql);
  }


  @Override
  public void manageAutoIncrement(Connection conn, TableElement t) throws SQLException {
    String sql;
    Statement stmt = conn.createStatement();
    try {
      // 1. Firstly, we have to clean up table from any auto-increment
      // defaults. Meanwhile we check if table has IDENTITY field, if it
      // doesn't, no need to proceed.
      IntegerColumn idColumn = null;
      for (Column c : t.getColumns().values())
        if (c instanceof IntegerColumn) {
          IntegerColumn ic = (IntegerColumn) c;
          if (ic.isIdentity())
            idColumn = ic;
          else {
            if (ic.getDefaultValue() == null) {
              sql = String.format("alter table %s.%s alter column %s drop default",
                  t.getGrain().getQuotedName(), t.getQuotedName(), ic.getQuotedName());
            } else {
              sql = String.format("alter table %s.%s alter column %s set default %d",
                  t.getGrain().getQuotedName(), t.getQuotedName(), ic.getQuotedName(),
                  ic.getDefaultValue().intValue());
            }
            stmt.executeUpdate(sql);
          }
        }

      if (idColumn == null)
        return;

      // 2. Now, we know that we surely have IDENTITY field, and we have
      // to be sure that we have an appropriate sequence.
      boolean hasSequence = false;
      sql = String.format(
          "select count(*) from pg_class c inner join pg_namespace n ON n.oid = c.relnamespace "
              + "where n.nspname = '%s' and c.relname = '%s_seq' and c.relkind = 'S'",
          t.getGrain().getName(), t.getName());
      ResultSet rs = stmt.executeQuery(sql);
      rs.next();
      try {
        hasSequence = rs.getInt(1) > 0;
      } finally {
        rs.close();
      }
      if (!hasSequence) {
        sql = String.format("create sequence \"%s\".\"%s_seq\" increment 1 minvalue 1", t.getGrain().getName(),
            t.getName());
        stmt.executeUpdate(sql);
      }

      // 3. Now we have to create the auto-increment default
      sql = String.format(
          "alter table %s.%s alter column %s set default " + "nextval('\"%s\".\"%s_seq\"'::regclass);",
          t.getGrain().getQuotedName(), t.getQuotedName(), idColumn.getQuotedName(), t.getGrain().getName(),
          t.getName());
      stmt.executeUpdate(sql);
    } finally {
      stmt.close();
    }
  }


  @SuppressWarnings("unchecked")
  @Override
  public DBColumnInfo getColumnInfo(Connection conn, Column c) throws CelestaException {
    try {
      DatabaseMetaData metaData = conn.getMetaData();
      ResultSet rs = metaData.getColumns(null, c.getParentTable().getGrain().getName(),
          c.getParentTable().getName(), c.getName());
      try {
        if (rs.next()) {
          DBColumnInfo result = new DBColumnInfo();
          result.setName(rs.getString(COLUMN_NAME));
          String typeName = rs.getString("TYPE_NAME");
          if ("serial".equalsIgnoreCase(typeName)) {
            result.setType(IntegerColumn.class);
            result.setIdentity(true);
            result.setNullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
            return result;
          } else if ("text".equalsIgnoreCase(typeName)) {
            result.setType(StringColumn.class);
            result.setMax(true);
          } else {
            for (Class<?> cc : COLUMN_CLASSES)
              if (TYPES_DICT.get(cc).dbFieldType().equalsIgnoreCase(typeName)) {
                result.setType((Class<? extends Column>) cc);
                break;
              }
          }
          result.setNullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
          if (result.getType() == StringColumn.class) {
            result.setLength(rs.getInt("COLUMN_SIZE"));
          }
          String defaultBody = rs.getString("COLUMN_DEF");
          if (defaultBody != null) {
            defaultBody = modifyDefault(result, defaultBody);
            result.setDefaultValue(defaultBody);
          }
          return result;
        } else {
          return null;
        }
      } finally {
        rs.close();
      }
    } catch (SQLException e) {
      throw new CelestaException(e.getMessage());
    }
  }

  private String modifyDefault(DBColumnInfo ci, String defaultBody) {
    String result = defaultBody;
    if (DateTimeColumn.class == ci.getType()) {
      if (NOW.equalsIgnoreCase(defaultBody))
        result = "GETDATE()";
      else {
        Matcher m = DATEPATTERN.matcher(defaultBody);
        m.find();
        result = String.format("'%s%s%s'", m.group(1), m.group(2), m.group(3));
      }
    } else if (BooleanColumn.class == ci.getType()) {
      result = "'" + defaultBody.toUpperCase() + "'";
    } else if (StringColumn.class == ci.getType()) {
      if (result.endsWith("::text"))
        result = result.substring(0, result.length() - "::text".length());
      else if (result.endsWith("::character varying"))
        result = result.substring(0, result.length() - "::character varying".length());
    } else if (BinaryColumn.class == ci.getType()) {
      Matcher m = HEX_STRING.matcher(defaultBody);
      if (m.find())
        result = "0x" + m.group(1).toUpperCase();
    }
    return result;
  }

  @Override
  protected void updateColType(Column c, DBColumnInfo actual, List<String> batch) {
    String sql;
    String colType;
    if (c.getClass() == StringColumn.class) {
      StringColumn sc = (StringColumn) c;
      colType = sc.isMax() ? "text" : String.format("%s(%s)", getColumnDefiner(c).dbFieldType(), sc.getLength());
    } else {
      colType = getColumnDefiner(c).dbFieldType();
    }
    // Если тип не совпадает
    if (c.getClass() != actual.getType()) {
      sql = String.format(ALTER_TABLE + tableTemplate() + " ALTER COLUMN \"%s\" TYPE %s",
          c.getParentTable().getGrain().getName(), c.getParentTable().getName(), c.getName(), colType);
      if (c.getClass() == IntegerColumn.class)
        sql += String.format(" USING (%s::integer);", c.getQuotedName());
      else if (c.getClass() == BooleanColumn.class)
        sql += String.format(" USING (%s::boolean);", c.getQuotedName());

      batch.add(sql);
    } else if (c.getClass() == StringColumn.class) {
      StringColumn sc = (StringColumn) c;
      if (sc.isMax() != actual.isMax() || sc.getLength() != actual.getLength()) {
        sql = String.format(ALTER_TABLE + tableTemplate() + " ALTER COLUMN \"%s\" TYPE %s",
            c.getParentTable().getGrain().getName(), c.getParentTable().getName(), c.getName(), colType);
        batch.add(sql);
      }
    }
  }


  @Override
  public String getInFilterClause(Table table, Table otherTable, List<String> fields, List<String> otherFields) {
    String template = "( %s ) IN (SELECT %s FROM %s )";
    String fieldsStr = String.join(",",
        fields.stream()
            .map(s -> "\"" + s + "\"")
            .collect(Collectors.toList())
    );
    String otherFieldsStr = String.join(",",
        otherFields.stream()
            .map(s -> "\"" + s + "\"")
            .collect(Collectors.toList())
    );

    String otherTableStr = String.format(tableTemplate(), otherTable.getGrain().getName(), otherTable.getName());
    String result = String.format(template, fieldsStr, otherFieldsStr, otherTableStr);
    return result;
  }

  @Override
  String[] getCreateIndexSQL(Index index) {

    StringBuilder sb = new StringBuilder();
    StringBuilder sb2 = new StringBuilder();
    boolean conjugate = false;
    for (Map.Entry<String, Column> c : index.getColumns().entrySet()) {
      if (sb.length() > 0) {
        sb.append(", ");
        sb2.append(", ");
      }
      sb.append('"');
      sb2.append('"');
      sb.append(c.getKey());
      sb2.append(c.getKey());
      sb.append('"');
      sb2.append('"');

      if (c.getValue() instanceof StringColumn && !((StringColumn) c.getValue()).isMax()) {
        sb2.append(" varchar_pattern_ops");
        conjugate = true;
      }
    }

    String sql = String.format("CREATE INDEX \"%s\" ON " + tableTemplate() + " (%s)", index.getName(),
        index.getTable().getGrain().getName(), index.getTable().getName(), sb.toString());
    if (conjugate) {
      String sql2 = String.format("CREATE INDEX \"%s\" ON " + tableTemplate() + " (%s)",
          index.getName() + CONJUGATE_INDEX_POSTFIX, index.getTable().getGrain().getName(),
          index.getTable().getName(), sb2.toString());
      String[] result = {sql, sql2};
      return result;
    } else {
      String[] result = {sql};
      return result;
    }

  }


  @Override
  String getLimitedSQL(GrainElement t, String whereClause, String orderBy, long offset, long rowCount) {
    if (offset == 0 && rowCount == 0)
      throw new IllegalArgumentException();
    String sql;
    if (offset == 0)
      sql = getSelectFromOrderBy(t, whereClause, orderBy) + String.format(" limit %d", rowCount);
    else if (rowCount == 0)
      sql = getSelectFromOrderBy(t, whereClause, orderBy) + String.format(" limit all offset %d", offset);
    else {
      sql = getSelectFromOrderBy(t, whereClause, orderBy)
          + String.format(" limit %d offset %d", rowCount, offset);
    }
    return sql;
  }


  @Override
  public DBPKInfo getPKInfo(Connection conn, TableElement t) throws CelestaException {
    String sql = String.format(
        "SELECT i.relname AS indexname, " + "i.oid, array_length(x.indkey, 1) as colcount " + "FROM pg_index x "
            + "INNER JOIN pg_class c ON c.oid = x.indrelid "
            + "INNER JOIN pg_class i ON i.oid = x.indexrelid "
            + "INNER JOIN pg_namespace n ON n.oid = c.relnamespace "
            + "WHERE c.relkind = 'r'::\"char\" AND i.relkind = 'i'::\"char\" "
            + "and n.nspname = '%s' and c.relname = '%s' and x.indisprimary",
        t.getGrain().getName(), t.getName());
    DBPKInfo result = new DBPKInfo();
    try {
      Statement stmt = conn.createStatement();
      PreparedStatement stmt2 = conn.prepareStatement("select pg_get_indexdef(?, ?, false)");
      try {
        ResultSet rs = stmt.executeQuery(sql);
        if (rs.next()) {
          String indName = rs.getString("indexname");
          int colCount = rs.getInt("colcount");
          int oid = rs.getInt("oid");
          result.setName(indName);
          stmt2.setInt(1, oid);
          for (int i = 1; i <= colCount; i++) {
            stmt2.setInt(2, i);
            ResultSet rs2 = stmt2.executeQuery();
            try {
              rs2.next();
              String colName = rs2.getString(1);
              Matcher m = QUOTED_NAME.matcher(colName);
              m.matches();
              result.addColumnName(m.group(1));
            } finally {
              rs2.close();
            }
          }
        }
      } finally {
        stmt.close();
        stmt2.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not get indices information: %s", e.getMessage());
    }
    return result;
  }

  @Override
  public void dropPK(Connection conn, TableElement t, String pkName) throws CelestaException {
    String sql = String.format("alter table %s.%s drop constraint \"%s\" cascade", t.getGrain().getQuotedName(),
        t.getQuotedName(), pkName);
    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeUpdate(sql);
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Cannot drop PK '%s': %s", pkName, e.getMessage());
    }
  }

  @Override
  public List<DBFKInfo> getFKInfo(Connection conn, Grain g) throws CelestaException {
    // Full foreign key information query
    String sql = String.format(
        "SELECT RC.CONSTRAINT_SCHEMA AS GRAIN" + "   , KCU1.CONSTRAINT_NAME AS FK_CONSTRAINT_NAME"
            + "   , KCU1.TABLE_NAME AS FK_TABLE_NAME" + "   , KCU1.COLUMN_NAME AS FK_COLUMN_NAME"
            + "   , KCU2.TABLE_SCHEMA AS REF_GRAIN" + "   , KCU2.TABLE_NAME AS REF_TABLE_NAME"
            + "   , RC.UPDATE_RULE, RC.DELETE_RULE " + "FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS RC "
            + "INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE KCU1 "
            + "   ON  KCU1.CONSTRAINT_CATALOG = RC.CONSTRAINT_CATALOG"
            + "   AND KCU1.CONSTRAINT_SCHEMA  = RC.CONSTRAINT_SCHEMA"
            + "   AND KCU1.CONSTRAINT_NAME    = RC.CONSTRAINT_NAME "
            + "INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE KCU2"
            + "   ON  KCU2.CONSTRAINT_CATALOG = RC.UNIQUE_CONSTRAINT_CATALOG"
            + "   AND KCU2.CONSTRAINT_SCHEMA  = RC.UNIQUE_CONSTRAINT_SCHEMA"
            + "   AND KCU2.CONSTRAINT_NAME    = RC.UNIQUE_CONSTRAINT_NAME"
            + "   AND KCU2.ORDINAL_POSITION   = KCU1.ORDINAL_POSITION "
            + "WHERE RC.CONSTRAINT_SCHEMA = '%s' " + "ORDER BY KCU1.CONSTRAINT_NAME, KCU1.ORDINAL_POSITION",
        g.getName());

    // System.out.println(sql);

    List<DBFKInfo> result = new LinkedList<>();
    try {
      Statement stmt = conn.createStatement();
      try {
        DBFKInfo i = null;
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
          String fkName = rs.getString("FK_CONSTRAINT_NAME");
          if (i == null || !i.getName().equals(fkName)) {
            i = new DBFKInfo(fkName);
            result.add(i);
            i.setTableName(rs.getString("FK_TABLE_NAME"));
            i.setRefGrainName(rs.getString("REF_GRAIN"));
            i.setRefTableName(rs.getString("REF_TABLE_NAME"));
            i.setUpdateRule(getFKRule(rs.getString("UPDATE_RULE")));
            i.setDeleteRule(getFKRule(rs.getString("DELETE_RULE")));
          }
          i.getColumnNames().add(rs.getString("FK_COLUMN_NAME"));
        }
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException(e.getMessage());
    }
    return result;
  }

  @Override
  public Map<String, DBIndexInfo> getIndices(Connection conn, Grain g) throws CelestaException {
    String sql = String.format("SELECT c.relname AS tablename, i.relname AS indexname, "
        + "i.oid, array_length(x.indkey, 1) as colcount " + "FROM pg_index x "
        + "INNER JOIN pg_class c ON c.oid = x.indrelid " + "INNER JOIN pg_class i ON i.oid = x.indexrelid "
        + "INNER JOIN pg_namespace n ON n.oid = c.relnamespace "
        + "WHERE c.relkind = 'r'::\"char\" AND i.relkind = 'i'::\"char\" "
        + "and n.nspname = '%s' and x.indisunique = false;", g.getName());
    Map<String, DBIndexInfo> result = new HashMap<>();
    try {
      Statement stmt = conn.createStatement();
      PreparedStatement stmt2 = conn.prepareStatement("select pg_get_indexdef(?, ?, false)");
      try {
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
          String tabName = rs.getString("tablename");
          String indName = rs.getString("indexname");
          if (indName.endsWith(CONJUGATE_INDEX_POSTFIX))
            continue;
          DBIndexInfo ii = new DBIndexInfo(tabName, indName);
          result.put(indName, ii);
          int colCount = rs.getInt("colcount");
          int oid = rs.getInt("oid");
          stmt2.setInt(1, oid);
          for (int i = 1; i <= colCount; i++) {
            stmt2.setInt(2, i);
            ResultSet rs2 = stmt2.executeQuery();
            try {
              rs2.next();
              String colName = rs2.getString(1);
              Matcher m = QUOTED_NAME.matcher(colName);
              m.matches();
              ii.getColumnNames().add(m.group(1));
            } finally {
              rs2.close();
            }
          }

        }
      } finally {
        stmt.close();
        stmt2.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not get indices information: %s", e.getMessage());
    }
    return result;
  }


  @Override
  public void createSysObjects(Connection conn) throws CelestaException {
    String sql = "CREATE OR REPLACE FUNCTION celesta.recversion_check()" + "  RETURNS trigger AS $BODY$ BEGIN\n"
        + "    IF (OLD.recversion = NEW.recversion) THEN\n"
        + "       NEW.recversion = NEW.recversion + 1;\n     ELSE\n"
        + "       RAISE EXCEPTION 'record version check failure';\n" + "    END IF;"
        + "    RETURN NEW; END; $BODY$\n" + "  LANGUAGE plpgsql VOLATILE COST 100;";

    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeUpdate(sql);
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not create or replace celesta.recversion_check() function: %s",
          e.getMessage());
    }

    //CREATE INSERT TRIGGER FOR MATERIALIZED VIEWS
    sql = "CREATE OR REPLACE FUNCTION celesta.materialized_view_insert() RETURNS trigger AS $BODY$ "
        + "DECLARE \n"
        + "mv                 varchar; "
        + "mvGrouByCols     varchar; "
        + "tableGroupByCols varchar; "
        + "mvAllCols        varchar; "
        + "selectStmt         varchar; \n"
        + "BEGIN \n"
        + "mv := TG_ARGV[0]; mvGrouByCols := TG_ARGV[1]; tableGroupByCols := TG_ARGV[2]; "
        + "mvAllCols := TG_ARGV[3]; selectStmt := TG_ARGV[4]; \n"
        + "LOCK TABLE ONLY mv IN ROW EXCLUSIVE MODE;\n"
        + "EXECUTE 'DELETE FROM ' || mv || ' WHERE (' || mvGrouByCols || ') IN (select ' || tableGroupByCols || ')' USING NEW; \n"
        + "EXECUTE 'INSERT INTO ' || mv  || '(' || format('%s', mvAllCols) || ') ' || selectStmt USING NEW; \n"
        + "RETURN NEW; END;\n $BODY$LANGUAGE plpgsql VOLATILE COST 100;";

    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeUpdate(sql);
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not create or replace celesta.materialized_view_insert function: %s",
          e.getMessage());
    }


    //CREATE UPDATE TRIGGER FOR MATERIALIZED VIEWS
    sql = "CREATE OR REPLACE FUNCTION celesta.materialized_view_insert() RETURNS trigger AS $BODY$ "
        + "DECLARE \n"
        + "mv                 varchar; "
        + "mvGrouByCols     varchar; "
        + "tableGroupByCols varchar; "
        + "mvAllCols        varchar; "
        + "selectStmt         varchar; \n"
        + "BEGIN \n"
        + "mv := TG_ARGV[0]; mvGrouByCols := TG_ARGV[1]; tableGroupByCols := TG_ARGV[2]; "
        + "mvAllCols := TG_ARGV[3]; selectStmt := TG_ARGV[4]; \n"
        + "LOCK TABLE ONLY mv IN ROW EXCLUSIVE MODE;\n"
        //process OLD
        + "EXECUTE 'DELETE FROM ' || mv || ' WHERE (' || mvGrouByCols || ') IN (select ' || tableGroupByCols || ')' USING OLD; \n"
        + "EXECUTE 'INSERT INTO ' || mv  || '(' || format('%s', mvAllCols) || ') ' || selectStmt USING OLD; \n"
        //process NEW
        + "EXECUTE 'DELETE FROM ' || mv || ' WHERE (' || mvGrouByCols || ') IN (select ' || tableGroupByCols || ')' USING NEW; \n"
        + "EXECUTE 'INSERT INTO ' || mv  || '(' || format('%s', mvAllCols) || ') ' || selectStmt USING NEW; \n"
        + "RETURN NEW; END;\n $BODY$LANGUAGE plpgsql VOLATILE COST 100;";

    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeUpdate(sql);
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not create or replace celesta.materialized_view_update function: %s",
          e.getMessage());
    }

    //CREATE INSERT TRIGGER FOR MATERIALIZED VIEWS
    sql = "CREATE OR REPLACE FUNCTION celesta.materialized_view_delete() RETURNS trigger AS $BODY$ "
        + "DECLARE \n"
        + "mv                 varchar; "
        + "mvGrouByCols     varchar; "
        + "tableGroupByCols varchar; "
        + "mvAllCols        varchar; "
        + "selectStmt         varchar; \n"
        + "BEGIN \n"
        + "mv := TG_ARGV[0]; mvGrouByCols := TG_ARGV[1]; tableGroupByCols := TG_ARGV[2]; "
        + "mvAllCols := TG_ARGV[3]; selectStmt := TG_ARGV[4]; \n"
        + "LOCK TABLE ONLY mv IN ROW EXCLUSIVE MODE;\n"
        + "EXECUTE 'DELETE FROM ' || mv || ' WHERE (' || mvGrouByCols || ') IN (select ' || tableGroupByCols || ')' USING OLD; \n"
        + "EXECUTE 'INSERT INTO ' || mv  || '(' || format('%s', mvAllCols) || ') ' || selectStmt USING OLD; \n"
        + "RETURN NEW; END;\n $BODY$LANGUAGE plpgsql VOLATILE COST 100;";

    try {
      Statement stmt = conn.createStatement();
      try {
        stmt.executeUpdate(sql);
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not create or replace celesta.materialized_view_delete function: %s",
          e.getMessage());
    }
  }


  @Override
  public boolean triggerExists(Connection conn, TriggerQuery query) throws SQLException {
    String sql = String.format("select count(*) from information_schema.triggers where "
        + "		event_object_schema = '%s' and event_object_table= '%s'"
        + "		and trigger_name = '%s'", query.getSchema(), query.getTableName(), query.getName());

    Statement stmt = conn.createStatement();
    try {
      ResultSet rs = stmt.executeQuery(sql);
      rs.next();
      boolean result = rs.getInt(1) > 0;
      rs.close();
      return result;
    } finally {
      stmt.close();
    }

  }

  @Override
  public void updateVersioningTrigger(Connection conn, TableElement t) throws CelestaException {
    // First of all, we are about to check if trigger exists

    try {
      Statement stmt = conn.createStatement();
      try {
        TriggerQuery query = new TriggerQuery().withSchema(t.getGrain().getName())
            .withName("versioncheck")
            .withTableName(t.getName());
        boolean triggerExists = triggerExists(conn, query);

        if (t instanceof VersionedElement) {
          VersionedElement ve = (VersionedElement) t;

          String sql;
          if (ve.isVersioned()) {
            if (triggerExists) {
              return;
            } else {
              // CREATE TRIGGER
              sql = String.format(
                  "CREATE TRIGGER \"versioncheck\"" + " BEFORE UPDATE ON " + tableTemplate()
                      + " FOR EACH ROW EXECUTE PROCEDURE celesta.recversion_check();",
                  t.getGrain().getName(), t.getName());
              stmt.executeUpdate(sql);
            }
          } else {
            if (triggerExists) {
              // DROP TRIGGER
              dropTrigger(conn, query);
            } else {
              return;
            }
          }
        }
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not update version check trigger on %s.%s: %s", t.getGrain().getName(),
          t.getName(), e.getMessage());
    }

  }


  @Override
  public int getDBPid(Connection conn) throws CelestaException {
    try {
      Statement stmt = conn.createStatement();
      try {
        ResultSet rs = stmt.executeQuery("select pg_backend_pid();");
        if (rs.next()) {
          return rs.getInt(1);
        } else {
          return 0;
        }
      } finally {
        stmt.close();
      }
    } catch (SQLException e) {
      throw new CelestaException(e.getMessage());
    }
  }

  @Override
  public void createTriggersForMaterializedView(Connection conn, MaterializedView mv) throws CelestaException {
    Table t = mv.getRefTable().getTable();

    String fullTableName = String.format(tableTemplate(), t.getGrain().getName(), t.getName());
    String fullMvName = String.format(tableTemplate(), mv.getGrain().getName(), mv.getName());

    String insertTriggerName = String.format("mvInsertFrom%s_%sTo%s_%s",
        t.getGrain().getName(), t.getName(), mv.getGrain().getName(), mv.getName());
    String updateTriggerName = String.format("mvUpdateFrom%s_%sTo%s_%s",
        t.getGrain().getName(), t.getName(), mv.getGrain().getName(), mv.getName());
    String deleteTriggerName = String.format("mvDeleteFrom%s_%sTo%s_%s",
        t.getGrain().getName(), t.getName(), mv.getGrain().getName(), mv.getName());

    String mvGroupByColumns = mv.getColumns().keySet().stream()
        .filter(alias -> mv.isGroupByColumn(alias))
        .collect(Collectors.joining(", "));

    String tGroupByColumns = mv.getColumns().keySet().stream()
        .filter(alias -> mv.isGroupByColumn(alias))
        .map(alias -> "$1." + alias)
        .collect(Collectors.joining(", "));

    String mvColumns = mv.getColumns().keySet().stream()
        .collect(Collectors.joining(", "));

    String whereCondition = mv.getColumns().keySet().stream()
        .filter(alias -> mv.isGroupByColumn(alias))
        .map(alias -> alias + " = $1." + alias + " ")
        .collect(Collectors.joining(" AND "));

    StringBuilder selectStmtBuilder = new StringBuilder(mv.getSelectPartOfScript())
        .append(" FROM ").append(fullTableName).append(" ");
    selectStmtBuilder.append(" WHERE ").append(whereCondition)
        .append(mv.getGroupByPartOfScript());


    String updateCondition = mv.getColumnRefNames().stream().map(
        colName -> new StringBuilder().append("OLD.").append(colName)
        .append(" IS DISTINCT FROM NEW.").append(colName).toString()
    ).collect(Collectors.joining(" OR "));

    String sql;
    Statement stmt = null;
    try {

      stmt = conn.createStatement();
      //INSERT
      try {
        sql = String.format("CREATE TRIGGER %s AFTER INSERT " +
                "ON %s FOR EACH ROW EXECUTE PROCEDURE celesta.materialized_view_insert('%s', '%s', '%s', '%s', '%s')",
            insertTriggerName, fullTableName, fullMvName, mvGroupByColumns, tGroupByColumns,
            mvColumns, selectStmtBuilder.toString());

        //System.out.println(sql);
        stmt.execute(sql);
      } catch (SQLException e) {
        throw new CelestaException("Could not update insert-trigger on %s for materialized view %s: %s",
            fullTableName, fullMvName, e);
      }
      //UPDATE
      try {
        sql = String.format("CREATE TRIGGER %s AFTER UPDATE " +
                "ON %s FOR EACH ROW " +
                "WHEN (" + updateCondition + ") " +
                "EXECUTE PROCEDURE celesta.materialized_view_update('%s', '%s', '%s', '%s', '%s')",
            updateTriggerName, fullTableName, fullMvName, mvGroupByColumns, tGroupByColumns,
            mvColumns, selectStmtBuilder.toString());

        System.out.println(sql);
        stmt.execute(sql);
      } catch (SQLException e) {
        throw new CelestaException("Could not update update-trigger on %s for materialized view %s: %s",
            fullTableName, fullMvName, e);
      }
      //DELETE
      try {
        sql = String.format("CREATE TRIGGER %s AFTER DELETE " +
                "ON %s FOR EACH ROW EXECUTE PROCEDURE celesta.materialized_view_delete('%s', '%s', '%s', '%s', '%s')",
            deleteTriggerName, fullTableName, fullMvName, mvGroupByColumns, tGroupByColumns,
            mvColumns, selectStmtBuilder.toString());

        System.out.println(sql);
        stmt.execute(sql);
      } catch (SQLException e) {
        throw new CelestaException("Could not update delete-trigger on %s for materialized view %s: %s",
            fullTableName, fullMvName, e);
      }
    } catch (SQLException e) {
      throw new CelestaException("Could not update triggers on %s for materialized view %s: %s",
          fullTableName, fullMvName, e);
    } finally {
      try {
        if (stmt != null)
          stmt.close();
      } catch (SQLException e) {
        //do nothing
      }
    }
  }

  @Override
  public void dropTriggersForMaterializedView(Connection conn, MaterializedView mv) throws CelestaException {
    Table t = mv.getRefTable().getTable();
    TriggerQuery query = new TriggerQuery()
        .withSchema(t.getGrain().getName())
        .withTableName(t.getName());

    String insertTriggerName = String.format("mvInsertFrom%s_%sTo%s_%s",
        t.getGrain().getName(), t.getName(), mv.getGrain().getName(), mv.getName());
    String updateTriggerName = String.format("mvUpdateFrom%s_%sTo%s_%s",
        t.getGrain().getName(), t.getName(), mv.getGrain().getName(), mv.getName());
    String deleteTriggerName = String.format("mvDeleteFrom%s_%sTo%s_%s",
        t.getGrain().getName(), t.getName(), mv.getGrain().getName(), mv.getName());

    try {
      query.withName(insertTriggerName);
      dropTrigger(conn, query);
      query.withName(updateTriggerName);
      dropTrigger(conn, query);
      query.withName(deleteTriggerName);
      dropTrigger(conn, query);
    } catch (SQLException e) {
      throw new CelestaException("Can't drop triggers for materialized view %s.%s: %s",
          mv.getGrain().getName(), mv.getName(), e.getMessage());
    }
  }
}