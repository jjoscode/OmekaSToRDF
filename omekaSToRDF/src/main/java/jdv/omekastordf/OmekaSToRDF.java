package jdv.omekastordf;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import java.sql.*;


public class OmekaSToRDF{ 

    private static String baseIRI = null;
    private static String dbUrl   = null;
    private static String username = null;
    private static String password = null;
    private static String serialization  = null;
    private static String typeToIRI= null;
    private static String idToPIRI = null;
    private static HashMap<String,String> pIRI = new HashMap<>();
    private static HashMap<String, String> type = new HashMap<>();
    private static Set<String> lang = new HashSet<>();

    public static ArrayList<String> generateGraph(String[] args){
        ArrayList<String> toDisplay = new ArrayList<>();
        if(checkFlag(args)){
            toDisplay = runMap();
        }
        else{
            System.out.println("ERRORE: dati mancanti");
            System.out.println("Base IRI: " + baseIRI);
            System.out.println("DB URL: " + dbUrl);
            System.out.println("Username: " + username);
            System.out.println("Password: " + password);
        }
        return toDisplay;
    }
    
    private static boolean checkFlag(String[] args){
        serialization = args[0];
        dbUrl = "jdbc:mysql://" + args[1];
        username = args[2];
        password = args[3];
        baseIRI = args[4];
        typeToIRI = args[5];
        idToPIRI = args[6];
        return baseIRI != null && dbUrl != null && password != null && username != null && typeToIRI != null && idToPIRI != null;
    }

    private static final String BASE_IRI_REGEX = 
        "^(https?|urn):\\/\\/?" +            
        "[\\w.-]+" +                         
        "(:\\d+)?"+                           
        "([/\\w.?=#%-]*)?" +                 
        "\\/?$";                              

    private static final Pattern BASE_IRI_PATTERN = Pattern.compile(BASE_IRI_REGEX);


    public static boolean isValidBaseIRI(String baseIRI) {
        if (baseIRI == null) return false;
        Matcher matcher = BASE_IRI_PATTERN.matcher(baseIRI);
        return matcher.matches();
    }

    private static final Pattern FORMAT_PATTERN = Pattern.compile("^(turtle|nquads|rdfxml|trig|trix|jsonld|hdt)$");

    public static boolean isValidFormat(String outputFormat) {
        if (outputFormat == null) return false;
        return FORMAT_PATTERN.matcher(outputFormat.toLowerCase()).matches();
    }

    public static void deleteOldOutputFile(){
        String[] outputFiles = {
                "target/output.ttl",
                "target/output.nq",
                "target/output.trig",
                "target/output.trix",
                "target/output.jsonld",
                "target/output.hdt",
                "target/output.rdf"
        };
        for (String path : outputFiles) {
            File file = new File(path);
            if (file.exists() && file.delete()) {
                System.out.println("Eliminato: " + path);
            }
        }
    }

    public static ArrayList<String> runMap() {
        writeStringToFile(idToPIRI,"./map/personalizedIRI.txt");
        writeStringToFile(typeToIRI, "./map/omekaTypeToRDF.txt");
        pIRI = getPIRIOrType(true);
        type = getPIRIOrType(false);
        createColumnPIRI();
        boolean rdf = false;
        deleteOldOutputFile();
        ArrayList<String> toDisplay = new ArrayList<>();
        String outputFile   = "target/output.ttl";
        try {
        String templatePath = "map/mapping-template.ttl";
        String mappingFile  = "map/mapping.ttl";

        switch (serialization) {
            case "nquads":
                outputFile = "target/output.nq";
                break;
            case "trig":
                outputFile = "target/output.trig";
                break;
            case "trix":
                outputFile = "target/output.trix";
                break;
            case "jsonld":
                outputFile = "target/output.jsonld";
                break;
            case "hdt":
                outputFile = "target/output.hdt";
                break;
            case "rdfxml":
                rdf = true;
                serialization = "turtle";
                outputFile = "target/output.ttl";
                break;
            case "turtle":
                outputFile = "target/output.ttl";
                break;
            default:
                outputFile = "target/output.rdf";
                break;
        }

        // --- LEGGE E RISCRIVE IL FILE MAPPING CON I VALORI DIRETTI ---
        try (BufferedReader reader = new BufferedReader(new FileReader(templatePath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(mappingFile))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("@base")) {
                    writer.write("@base <" + baseIRI + "> .");
                    writer.newLine();
                } else if (line.contains("d2rq:jdbcDSN")) {
                    // Sostituzione dinamica della connessione DB
                    writer.write("    d2rq:jdbcDSN \"" + dbUrl + "\" ;");
                    writer.newLine();
                } else if (line.contains("d2rq:username")) {
                    writer.write("    d2rq:username \"" + username + "\" ;");
                    writer.newLine();
                } else if (line.contains("d2rq:password")) {
                    writer.write("    d2rq:password \"" + password + "\" .");
                    writer.newLine();
                } else {
                    writer.write(line);
                    writer.newLine();
                }
            }



            int i = 0;
            writer.write(getIRIMap(i));
            for (Map.Entry<String, String> entry : type.entrySet()) {
                writer.write(getTypeMap(i, entry.getKey(), entry.getValue()));
                System.out.println(entry.getKey() + " -> " + entry.getValue());
                i++;
            }
            if(type.isEmpty()){
                writer.write(getUntipedMap());
            }
            i = 0;
            lang = getUniqueLangValues();
            lang.remove("");
            for ( String lang: lang ) {
                writer.write(getLangMap(i, lang));
                i++;
            }
        }

        // --- ESECUZIONE RML MAPPER ---
        String rmlMapperJar = "map/rmlmapper-7.3.3-r374-all.jar";
        String command = String.format(
            "java -jar %s -m %s -o %s -s %s",
            rmlMapperJar, mappingFile, outputFile, serialization
        );

        System.out.println("Eseguo comando: " + command);

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(new File(System.getProperty("user.dir")));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader processReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = processReader.readLine()) != null) {
                System.out.println(line + "\n");
            }
        }

        int exitCode = process.waitFor();
        System.out.println("Processo terminato con codice: " + exitCode);

    } catch (Exception e) {
        e.printStackTrace();
    }
        if(rdf == true){
            try{
                convertTurtleToRDFXML("target/output.ttl", "target/output.rdf");
                outputFile = "target/output.rdf";
            }
            catch(Exception e){
                System.out.println(e.getMessage());
            }
        }
        try(BufferedReader reader = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                toDisplay.add(line);
            }
        }catch (Exception e) {
            System.out.println("ERRORE: " + e.getMessage());
        }
        dropColumnsPIRI();
    return toDisplay;
}


    private static HashMap<String,String> getPIRIOrType(boolean b){
        HashMap<String, String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(b?"./map/personalizedIRI.txt":"./map/omekaTypeToRDF.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                    // Ignora righe vuote
                if (line.trim().isEmpty()) continue;

                    // Divide la riga sulla prima virgola
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    map.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private static void convertTurtleToRDFXML(String inputFile, String outputFile) throws IOException {
        try (FileInputStream in = new FileInputStream(inputFile);
             FileOutputStream out = new FileOutputStream(outputFile)) {

            Model model = Rio.parse(in, "", RDFFormat.TURTLE);
            Rio.write(model, out, RDFFormat.RDFXML);
            File file = new File("target/output.ttl");
            if (file.exists() && file.delete()) {
                System.out.println("Eliminato: " + "target/output.ttl");
            }
        }
    }

    public static void writeStringToFile(String content, String filePath) {
        if (content == null) {
            content = ""; // oppure lancia un'eccezione, a seconda di cosa vuoi fare
        }

        try (FileWriter writer = new FileWriter(filePath)) { // try-with-resources
            writer.write(content);
            System.out.println("Scrittura completata in " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createColumnPIRI(){
        Connection conn = null;
        Statement stmt = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl, username, password);
            stmt = conn.createStatement();
            // 1. aggiungi le colonne
            String sqlAlter =
                    "ALTER TABLE value " +
                            "ADD COLUMN SubjPIRI VARCHAR(100), " +
                            "ADD COLUMN ObjPIRI VARCHAR(100)";
            stmt.executeUpdate(sqlAlter);
            for(Map.Entry<String, String> entry : pIRI.entrySet()) {
                // 2. inserisci un nuovo record (specificando le nuove colonne)
                String sql = "UPDATE value SET subjPIRI = ? WHERE resource_id = ?";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, entry.getValue());
                ps.setInt(2, Integer.parseInt(entry.getKey()));
                ps.executeUpdate();

                sql = "UPDATE value SET objPIRI = ? WHERE value_resource_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, entry.getValue());
                ps.setInt(2, Integer.parseInt(entry.getKey()));
                ps.executeUpdate();
            }

            System.out.println("Schema aggiornato e record inserito con successo.");

        } catch (SQLException e) {
            e.printStackTrace();
            // qui potresti fare rollback se hai disabilitato l’auto-commit
        } finally {
            try { if (pstmt != null) pstmt.close(); } catch (SQLException ex) {}
            try { if (stmt != null) stmt.close(); } catch (SQLException ex) {}
            try { if (conn != null) conn.close(); } catch (SQLException ex) {}
        }
    }

    private static void dropColumnsPIRI() {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl, username, password);
            stmt = conn.createStatement();

            String tableName = "value"; // nome della tabella
            String[] columns = {"SubjPIRI", "ObjPIRI"};

            for (String col : columns) {
                // Controllo se la colonna esiste
                String checkSql = String.format(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s' AND COLUMN_NAME = '%s'",
                        tableName, col
                );
                try (ResultSet rs = stmt.executeQuery(checkSql)) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        String dropSql = String.format("ALTER TABLE %s DROP COLUMN %s", tableName, col);
                        stmt.executeUpdate(dropSql);
                        System.out.println("Colonna " + col + " eliminata con successo.");
                    } else {
                        System.out.println("Colonna " + col + " non esiste, salto il DROP.");
                    }
                }
            }

            System.out.println("Rimozione colonne completata.");

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try { if (stmt != null) stmt.close(); } catch (SQLException ex) {}
            try { if (conn != null) conn.close(); } catch (SQLException ex) {}
        }
    }

    private static String getIRIMap(int i){
        return "<#TriplesMap_ItemIRItoIRI"+i+">\n" +
                "    a rr:TriplesMap ;\n" +
                "\n" +
                "    rml:logicalSource [\n" +
                "        rml:source <#DB_source> ;\n" +
                "        rml:referenceFormulation ql:SQL ;\n" +
                "        rml:query \"SELECT resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id) JOIN property on value.property_id = property.id WHERE value.subjPIRI is NULL AND value.objPIRI is NULL;\"\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:subjectMap [\n" +
                "        rr:template \"{id}\" ;\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:predicateObjectMap [\n" +
                "        rr:predicate type:type ;\n" +
                "        rr:objectMap [\n" +
                "            rml:reference \"urilabel\";\n" +
                "            rr:termType rr:IRI;\n" +
                "        ] ;\n" +
                "    ] ;\n" +
                "\n" +
                "rr:predicateObjectMap [\n" +
                "    rr:predicateMap [\n" +
                "        rml:reference \"urilocalname\" ;\n" +
                "        rr:termType rr:IRI\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:objectMap [\n" +
                "        rr:template \"{valueID}\" ;\n" +
                "    ]\n" +
                "] .\n" +
                "<#TriplesMap_ItemIRItoPIRI>\n" +
                "    a rr:TriplesMap ;\n" +
                "\n" +
                "    rml:logicalSource [\n" +
                "        rml:source <#DB_source> ;\n" +
                "        rml:referenceFormulation ql:SQL ;\n" +
                "        rml:query \"SELECT value.ObjPIRI as ObjPIRI, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id) JOIN property on value.property_id = property.id WHERE value.subjPIRI is NULL AND value.objPIRI is NOT NULL;\"\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:subjectMap [\n" +
                "        rr:template \"{id}\" ;\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:predicateObjectMap [\n" +
                "        rr:predicate type:type ;\n" +
                "        rr:objectMap [\n" +
                "            rml:reference \"urilabel\";\n" +
                "            rr:termType rr:IRI;\n" +
                "        ] ;\n" +
                "    ] ;\n" +
                "\n" +
                "rr:predicateObjectMap [\n" +
                "    rr:predicateMap [\n" +
                "        rml:reference \"urilocalname\" ;\n" +
                "        rr:termType rr:IRI\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:objectMap [\n" +
                "        rml:reference \"ObjPIRI\" ;\n" +
                "    ]\n" +
                "] .\n" +
                "<#TriplesMap_ItemPIRItoIRI>\n" +
                "    a rr:TriplesMap ;\n" +
                "\n" +
                "    rml:logicalSource [\n" +
                "        rml:source <#DB_source> ;\n" +
                "        rml:referenceFormulation ql:SQL ;\n" +
                "        rml:query \"SELECT value.SubjPIRI AS SubjPIRI, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id) JOIN property on value.property_id = property.id WHERE value.subjPIRI is NOT NULL AND value.objPIRI is NULL;\"\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:subjectMap [\n" +
                "        rml:reference \"SubjPIRI\" ;\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:predicateObjectMap [\n" +
                "        rr:predicate type:type ;\n" +
                "        rr:objectMap [\n" +
                "            rml:reference \"urilabel\";\n" +
                "            rr:termType rr:IRI;\n" +
                "        ] ;\n" +
                "    ] ;\n" +
                "\n" +
                "rr:predicateObjectMap [\n" +
                "    rr:predicateMap [\n" +
                "        rml:reference \"urilocalname\" ;\n" +
                "        rr:termType rr:IRI\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:objectMap [\n" +
                "        rr:template \"{valueID}\" ;\n" +
                "    ]\n" +
                "] ." +
                "<#TriplesMap_ItemPIRItoPIRI>\n" +
                "    a rr:TriplesMap ;\n" +
                "\n" +
                "    rml:logicalSource [\n" +
                "        rml:source <#DB_source> ;\n" +
                "        rml:referenceFormulation ql:SQL ;\n" +
                "        rml:query \"SELECT value.SubjPIRI as SubjPIRI, value.ObjPIRI as ObjPIRI, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id) JOIN property on value.property_id = property.id WHERE value.subjPIRI is NOT NULL AND value.objPIRI is NOT NULL;\"\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:subjectMap [\n" +
                "        rml:reference \"SubjPIRI\" ;\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:predicateObjectMap [\n" +
                "        rr:predicate type:type ;\n" +
                "        rr:objectMap [\n" +
                "            rml:reference \"urilabel\";\n" +
                "            rr:termType rr:IRI;\n" +
                "        ] ;\n" +
                "    ] ;\n" +
                "\n" +
                "rr:predicateObjectMap [\n" +
                "    rr:predicateMap [\n" +
                "        rml:reference \"urilocalname\" ;\n" +
                "        rr:termType rr:IRI\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:objectMap [\n" +
                "        rml:reference \"ObjPIRI\" ;\n" +
                "    ]\n" +
                "] .";
    }

    private static String getTypeMap(int i, String key, String value){
        return "<#TriplesMap_Item"+i+">\n" + //
                "    a rr:TriplesMap ;\n" + //
                "\n" + //
                "    rml:logicalSource [\n" + //
                "        rml:source <#DB_source> ;\n" + //
                "        rml:referenceFormulation ql:SQL ;\n" + //
                "        rml:query \"SELECT resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id AND value.type = '"+key+"') JOIN property on value.property_id = property.id WHERE value.subjPIRI is NULL AND value.objPIRI is NULL;\"\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:subjectMap [\n" + //
                "        rr:template \"{id}\" ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:predicateObjectMap [\n" + //
                "        rr:predicate type:type ;\n" + //
                "        rr:objectMap [\n" + //
                "            rml:reference \"urilabel\";\n" + //
                "            rr:termType rr:IRI;\n" + //
                "        ] ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "rr:predicateObjectMap [\n" + //
                "    rr:predicateMap [\n" + //
                "        rml:reference \"urilocalname\" ;\n" + //
                "        rr:termType rr:IRI\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:objectMap [\n" + //
                "        rml:reference \"value\" ;\n" + //
                "        rr:datatype <"+value+">\n" + //
                ";\n" + //
                "    ]\n" + //
                "] .\n" +
                "<#TriplesMap_ItemPIRI"+i+">\n" + //
                "    a rr:TriplesMap ;\n" + //
                "\n" + //
                "    rml:logicalSource [\n" + //
                "        rml:source <#DB_source> ;\n" + //
                "        rml:referenceFormulation ql:SQL ;\n" + //
                "        rml:query \"SELECT value.SubjPIRI as SubjPIRI, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id AND value.type = '"+key+"') JOIN property on value.property_id = property.id WHERE value.subjPIRI is NOT NULL AND value.objPIRI is NULL;\"\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:subjectMap [\n" + //
                "        rml:reference \"SubjPIRI\" ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:predicateObjectMap [\n" + //
                "        rr:predicate type:type ;\n" + //
                "        rr:objectMap [\n" + //
                "            rml:reference \"urilabel\";\n" + //
                "            rr:termType rr:IRI;\n" + //
                "        ] ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "rr:predicateObjectMap [\n" + //
                "    rr:predicateMap [\n" + //
                "        rml:reference \"urilocalname\" ;\n" + //
                "        rr:termType rr:IRI\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:objectMap [\n" + //
                "        rml:reference \"value\" ;\n" + //
                "        rr:datatype <"+value+">\n" + //
                ";\n" + //
                "    ]\n" + //
                "] .\n";
    }

    public static Set<String> getUniqueLangValues() {
        Set<String> langs = new HashSet<>();
        String sql = "SELECT DISTINCT lang FROM value";

        try (Connection conn = DriverManager.getConnection(dbUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String lang = rs.getString("lang");
                if (lang != null) {
                    langs.add(lang);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return langs;
    }

    private static String getLangMap(int i, String lang) {
        return "<#TriplesMap_ItemLang"+i+">\n" + //
                "    a rr:TriplesMap ;\n" + //
                "\n" + //
                "    rml:logicalSource [\n" + //
                "        rml:source <#DB_source> ;\n" + //
                "        rml:referenceFormulation ql:SQL ;\n" + //
                "        rml:query \"SELECT value.lang AS lang, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id AND value.lang = '"+lang+"') JOIN property on value.property_id = property.id WHERE value.subjPIRI is NULL AND value.objPIRI is NULL;\"\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:subjectMap [\n" + //
                "        rr:template \"{id}\" ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:predicateObjectMap [\n" + //
                "        rr:predicate type:type ;\n" + //
                "        rr:objectMap [\n" + //
                "            rml:reference \"urilabel\";\n" + //
                "            rr:termType rr:IRI;\n" + //
                "        ] ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "rr:predicateObjectMap [\n" + //
                "    rr:predicateMap [\n" + //
                "        rml:reference \"urilocalname\" ;\n" + //
                "        rr:termType rr:IRI\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:objectMap [\n" + //
                "        rml:reference \"value\" ;\n" + //
                "        rr:language \""+lang+"\"\n" + //
                ";\n" + //
                "    ]\n" + //
                "] .\n" +
                "<#TriplesMap_ItemLangPIRI"+i+">\n" + //
                "    a rr:TriplesMap ;\n" + //
                "\n" + //
                "    rml:logicalSource [\n" + //
                "        rml:source <#DB_source> ;\n" + //
                "        rml:referenceFormulation ql:SQL ;\n" + //
                "        rml:query \"SELECT value.SubjPIRI as SubjPIRI, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id AND value.lang = '"+lang+"' ) JOIN property on value.property_id = property.id WHERE value.subjPIRI is NOT NULL AND value.objPIRI is NULL;\"\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:subjectMap [\n" + //
                "        rml:reference \"SubjPIRI\" ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:predicateObjectMap [\n" + //
                "        rr:predicate type:type ;\n" + //
                "        rr:objectMap [\n" + //
                "            rml:reference \"urilabel\";\n" + //
                "            rr:termType rr:IRI;\n" + //
                "        ] ;\n" + //
                "    ] ;\n" + //
                "\n" + //
                "rr:predicateObjectMap [\n" + //
                "    rr:predicateMap [\n" + //
                "        rml:reference \"urilocalname\" ;\n" + //
                "        rr:termType rr:IRI\n" + //
                "    ] ;\n" + //
                "\n" + //
                "    rr:objectMap [\n" + //
                "        rml:reference \"value\" ;\n" + //
                "        rr:language \""+lang+"\"\n" + //
                ";\n" + //
                "    ]\n" + //
                "] .\n";
    }

    private static String getUntipedMap(){
        return "<#TriplesMap_untiped>\n" +
                "    a rr:TriplesMap ;\n" +
                "\n" +
                "    rml:logicalSource [\n" +
                "        rml:source <#DB_source> ;\n" +
                "        rml:referenceFormulation ql:SQL ;\n" +
                "        rml:query \"SELECT resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id ) JOIN property on value.property_id = property.id WHERE value.SubjPIRI is NULL AND value.ObjPIRI is NULL;\"\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:subjectMap [\n" +
                "        rr:template \"{id}\" ;\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:predicateObjectMap [\n" +
                "        rr:predicate type:type ;\n" +
                "        rr:objectMap [\n" +
                "            rml:reference \"urilabel\";\n" +
                "            rr:termType rr:IRI;\n" +
                "        ] ;\n" +
                "    ] ;\n" +
                "\n" +
                "rr:predicateObjectMap [\n" +
                "    rr:predicateMap [\n" +
                "        rml:reference \"urilocalname\" ;\n" +
                "        rr:termType rr:IRI\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:objectMap [\n" +
                "        rml:reference \"value\" ;\n" +
                "    ]\n" +
                "] .\n" +
                "<#TriplesMap_untipedPIRI>\n" +
                "    a rr:TriplesMap ;\n" +
                "\n" +
                "    rml:logicalSource [\n" +
                "        rml:source <#DB_source> ;\n" +
                "        rml:referenceFormulation ql:SQL ;\n" +
                "        rml:query \"SELECT value.SubjPIRI as SubjPIRI, resource.id AS id, value.type AS type ,value.value_resource_id AS valueID ,CONCAT(vocabulary.namespace_uri, resource_class.local_name) AS urilabel, value.value AS value, value.type AS type, property.local_name, CONCAT(vocabulary.namespace_uri, property.local_name) AS urilocalname FROM resource join resource_class on resource.resource_class_id = resource_class.id join vocabulary on vocabulary.id = resource_class.vocabulary_id JOIN value ON (resource.id = value.resource_id ) JOIN property on value.property_id = property.id WHERE value.SubjPIRI is NULL AND value.ObjPIRI is NULL;\"\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:subjectMap [\n" +
                "        rml:reference \"SubjPIRI\" ;\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:predicateObjectMap [\n" +
                "        rr:predicate type:type ;\n" +
                "        rr:objectMap [\n" +
                "            rml:reference \"urilabel\";\n" +
                "            rr:termType rr:IRI;\n" +
                "        ] ;\n" +
                "    ] ;\n" +
                "\n" +
                "rr:predicateObjectMap [\n" +
                "    rr:predicateMap [\n" +
                "        rml:reference \"urilocalname\" ;\n" +
                "        rr:termType rr:IRI\n" +
                "    ] ;\n" +
                "\n" +
                "    rr:objectMap [\n" +
                "        rml:reference \"value\" ;\n" +
                "    ]\n" +
                "] .";
    }



}

