import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;

public class DbUser extends DbBasic{

    //variable declaration
    private DatabaseMetaData md = null;
    private ArrayList<String> tableNames = new ArrayList<>();
    private ArrayList<String> columns = new ArrayList<>();
    private ArrayList<Integer> push = new ArrayList<>();
    private ArrayList<ArrayList<String>> frkeyList;
    
    /**
     * dbUser class constructor
     * @param dbName the database we are analysing.
     * @throws Exception
     */
    public DbUser(String dbName) throws Exception{
        super(dbName);
        md = con.getMetaData();
    }

    /**
     * getting table names that the database contains
     * @return all the table names the database contains.
     * @throws Exception
     */
    public ArrayList<String> getTables() throws Exception {
        String[] types = {"TABLE"};
        ResultSet rs = md.getTables(null, null, "%", types);
        while (rs.next()) {
            tableNames.add(rs.getString("TABLE_NAME"));
        }
        frkeyList = new ArrayList<>(tableNames.size());
        return tableNames;
    }

    /**
     * getting all the columns that the table set in the parameter contains
     * @param tableName table name for which we get all the column names
     * @return an arraylist of all the column names this table contains
     * @throws Exception
     */
    public ArrayList<String> getColumns(String tableName) throws Exception{
        Statement stmt = con.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);
        ResultSetMetaData rsmd = rs.getMetaData();
        for(int i = 1; i <= rsmd.getColumnCount(); i++){
            columns.add(rsmd.getColumnName(i));
        }
        return columns;
    }

    /**
     * getting all the column data from the table that is set in the parameter
     * @param table table name from which we print all the column data
     * @return String of insert into statements
     * @throws Exception
     */
    public String printColumnData(String table) throws Exception{
        String stmt = "";
        Statement statement = con.createStatement();
        ResultSet results = statement.executeQuery("SELECT * FROM " + table);
        ArrayList<String> column = getColumns(table);
        //iterating through the column data and printing getting all the data
        while(results.next()){
            stmt += "INSERT INTO " + table + " VALUES( ";
            for(int i = 0; i < column.size()-1; i++){
                if(((ResultSetMetaData) results).getColumnType(i+1) == 12){
                    if(results.getString(column.get(i)).contains("'")){
                        stmt += "'" + results.getString(column.get(i)).replaceAll("'", "''") + "'" + ", ";
                    }else{
                        stmt += "'" + results.getString(column.get(i)) + "'" + ", ";
                    }
                }else{
                    stmt += results.getInt(column.get(i)) + ", ";
                }
            }
            if(((ResultSetMetaData) results).getColumnType(column.size()) == 12){
                stmt += "'" + results.getString(column.get(column.size()-1)).replaceAll("'", "''") + "'" + ");\n";
            }else{
                stmt += results.getInt(column.get(column.size()-1)) + ");\n";
            }
        }
        column.clear();
        return stmt;
    }
    /**
     * getting all the table statements meathod
     * @return Arraylist of create Table statements
     * @throws Exception
     */
    public ArrayList<String> createTables() throws Exception {
        ArrayList<String> prKeyList = new ArrayList<String>();
        DatabaseMetaData meta = con.getMetaData();
        String[] types = { "TABLE" };
        ResultSet table = meta.getTables(null, null, "%", types);
        ResultSet rs = null;
        ArrayList<String> tables = new ArrayList<>();
        ArrayList<String> tableStmt = new ArrayList<>();
        int i = 0;
        //iterating through the tables and creating the statements for sqlite
        while(table.next()){
            frkeyList.add(new ArrayList());
            String t = "CREATE TABLE ";
            String tableName = table.getString("TABLE_NAME");
            tables.add(tableName);
            t += tableName + "(\n\t";
            String tableCatalog = table.getString("TABLE_CAT");
            rs = meta.getColumns(null, null, tableName, null);
            //iterating through the primary keys and adding them to the arraylist which we will use later
            ResultSet primaryKeys = meta.getPrimaryKeys(tableCatalog, null, tableName);
            while(primaryKeys.next()){
                String prKey = primaryKeys.getString("COLUMN_NAME");
                prKeyList.add(prKey);
            }
            //getting all columns names, types and assigning them if they are primary keys
            int start = 0;
            while(rs.next()){
                start++;
                String columnName = rs.getString("COLUMN_NAME");
                String columnType = rs.getString("TYPE_NAME");
                if(start != 1){
                    if(prKeyList.size() == 1 && prKeyList.get(0).equals(columnName)){
                        t += ",\n\t" + columnName + " " + columnType + " " + "primary key";
                    }else{
                        t += ",\n\t" + columnName + " " + columnType;
                    }
                }
                else if(start == 1){
                    if(prKeyList.size() == 1 && prKeyList.get(0).equals(columnName)){
                        t += columnName + " " + columnType + " " + "primary key";
                    }else{
                        t += columnName + " " + columnType;
                    }
                }
                if(rs.getString("IS_NULLABLE").equals("N")){
                    t += " not null";
                }
            }
            //printing primary keys if there are more than one primary key
            if(prKeyList.size() > 1){
                t += ",\n\tprimary key(";
                for(int j = 0; j < prKeyList.size(); j++){
                    if(j != 0){
                        t += ", " + prKeyList.get(j);
                    }
                    else{
                        t += prKeyList.get(j);
                    }
                    
                }
                t += ")";
            }
            //getting all the foreign keys, their references, and what they column names they reference
            ResultSet frKeys = meta.getImportedKeys(tableCatalog, null, tableName);
            while(frKeys.next()){
                String frKey = frKeys.getString("FKCOLUMN_NAME");
                String frKeyRef = frKeys.getString("PKTABLE_NAME");
                String prKeyName = frKeys.getString("PKCOLUMN_NAME");
                frkeyList.get(i).add(frKeyRef);
                // System.out.println(frkeyList.get(i));
                if(!tables.contains(frKeyRef)){
                    //System.out.println(i);
                    tables.remove(frKeyRef);
                    //System.out.println(frKeyRef);
                    push.add(i);
                }
                t += ",\n\tforeign key(" + frKey + ") references " + frKeyRef + "(" + prKeyName + ")";
            }
            if(frkeyList.get(i).size() == 0){
                frkeyList.get(i).add(null);
            }
            t += "\n);\n";
            tableStmt.add(t);
            prKeyList.clear();
            i++;
        }
        return tableStmt;
    }
    /**
     * creating index statements and getting all the values of it
     * @param table table which we creating index for.
     * @throws SQLException
     */
    public void createIndex(String table) throws SQLException {
        ResultSet index = md.getIndexInfo(null, null, table, true, false);
        while(index.next()){
            String colName = index.getString("COLUMN_NAME");
            String tableName = index.getString("TABLE_NAME");
            String indexName = index.getString("INDEX_NAME");
            String ascOrDes = index.getString("ASC_OR_DESC");
            indexName = indexName.replaceAll(" ", "_");
            ascOrDes = "ASC"; //setting only because getIndexInfo has a bug which doesnt return the ASC or DESC result
            if(indexName.contains("sqlite_autoindex")){
            }else{
                System.out.println("CREATE INDEX "+ indexName + " ON " + tableName + " (" + colName + " " + ascOrDes + ");");
            }
        }
    }

    //sorting tables in the order that foreign keys are after the tables it reference
    public void printSortedTables(ArrayList<String> tableStmt, ArrayList<String> tableNames){
        int n = tableStmt.size();
        //System.out.println(n);
        ArrayList<String> existingTables = new ArrayList<>();
        for(int i = 0; i < frkeyList.size(); i++){
            if(frkeyList.get(i).contains(tableNames.get(i))){
                frkeyList.get(i).remove(tableNames.get(i));
                if(frkeyList.get(i).size() == 0){
                    frkeyList.get(i).add(null);
                }
            }
        }
        while(n != 0){
            for(int i = 0; i < tableStmt.size(); i++){
                //printing the first tables that dont have a foreign key
                if((frkeyList.get(i).get(0) == null && !existingTables.contains(tableNames.get(i)))){
                    System.out.println(tableStmt.get(i));
                    existingTables.add(tableNames.get(i));
                    n--;
                }
                else{
                    //printing tables that contain foreign keys
                    for(int j = 0; j < frkeyList.size(); j++){
                        if(existingTables.containsAll(frkeyList.get(j)) && existingTables.contains(tableNames.get(j)) == false){
                            System.out.println(tableStmt.get(j));
                            existingTables.add(tableNames.get(j));
                            n--;
                        } 
                    }   
                }
            }
        }
    }

    //main method
    public static void main(String[] args) throws Exception{
        String dbName = "LSH.db";
        DbUser d = new DbUser(dbName);
        ArrayList<String> tables;
        ArrayList<String> tableStmt = new ArrayList<>();
        ArrayList<String> columnData = new ArrayList<>();
        //getting the tables
        tables = d.getTables();
        String[] t = tables.toArray(new String[0]);
        //creating the tables and assigning it to an arraylist
        tableStmt = d.createTables();
        d.printSortedTables(tableStmt, tables);
        //printing all indexes
        for(int i = 0; i < t.length; i++){
            d.createIndex(t[i]);
        }
        //printing all the data that the database contains
        for(int i = 0; i < tables.size(); i++){
            columnData.add(d.printColumnData(tables.get(i)));
            System.out.println(columnData.get(i));
        }
        d.close();
    }

}