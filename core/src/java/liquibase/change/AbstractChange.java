package liquibase.change;

import liquibase.ChangeSet;
import liquibase.FileOpener;
import liquibase.database.Database;
import liquibase.database.statement.SqlStatement;
import liquibase.database.statement.generator.GeneratorValidationErrors;
import liquibase.database.statement.generator.SqlGenerator;
import liquibase.database.statement.generator.SqlGeneratorFactory;
import liquibase.database.statement.syntax.Sql;
import liquibase.database.structure.DatabaseObject;
import liquibase.exception.InvalidChangeDefinitionException;
import liquibase.exception.RollbackImpossibleException;
import liquibase.exception.SetupException;
import liquibase.exception.UnsupportedChangeException;
import liquibase.changelog.parser.xml.XMLChangeLogSerializer;
import liquibase.changelog.parser.string.StringChangeLogSerializer;
import liquibase.util.MD5Util;
import liquibase.util.StringUtils;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.*;

/**
 * Standard superclass for Changes to implement. This is a <i>skeletal implementation</i>,
 * as defined in Effective Java#16.
 *
 * @see Change
 */
public abstract class AbstractChange implements Change {

    @ChangeMetaDataField
    private ChangeMetaData changeMetaData;

    @ChangeMetaDataField
    private FileOpener fileOpener;

    @ChangeMetaDataField
    private ChangeSet changeSet;

    /**
     * Constructor with tag name and name
     *
     * @param changeName the tag name for this change
     * @param changeDescription the name for this change
     */
    protected AbstractChange(String changeName, String changeDescription) {
        this.changeMetaData = new ChangeMetaData(changeName,changeDescription);
    }

    public int getSpecializationLevel() {
        return SPECIALIZATION_LEVEL_DEFAULT;
    }

    public boolean supports(Database database) {
        return true;
    }

    public ChangeMetaData getChangeMetaData() {
        return changeMetaData;
    }

    public ChangeSet getChangeSet() {
        return changeSet;
    }

    public void setChangeSet(ChangeSet changeSet) {
        this.changeSet = changeSet;
    }

    public void validate(Database database) throws InvalidChangeDefinitionException {
        try {
            for (SqlStatement statement : generateStatements(database)) {
                GeneratorValidationErrors validationErrors = SqlGeneratorFactory.getInstance().getBestGenerator(statement, database).validate(statement, database);
                if (validationErrors.hasErrors()) {
                    throw new InvalidChangeDefinitionException("Change is invalid for "+database+": "+StringUtils.join(validationErrors.getErrorMessages(), ", "), this);
                }
            }
        } catch (UnsupportedChangeException e) {
            throw new InvalidChangeDefinitionException("Change is invalid for "+database+": "+e.getMessage(), this);
        }
    }


    /*
    * Skipped by this skeletal implementation
    *
    * @see liquibase.change.Change#generateStatements(liquibase.database.Database)
    */

    /**
     * @see liquibase.change.Change#generateRollbackStatements(liquibase.database.Database)
     */
    public SqlStatement[] generateRollbackStatements(Database database) throws UnsupportedChangeException, RollbackImpossibleException {
        return generateRollbackStatementsFromInverse(database);
    }

    /**
     * @see liquibase.change.Change#supportsRollback()
     */
    public boolean supportsRollback() {
        return createInverses() != null;
    }

    /**
     * @see liquibase.change.Change#generateCheckSum()
     */
    public CheckSum generateCheckSum() {
            return new CheckSum(new StringChangeLogSerializer().serialize(this));
    }

    //~ ------------------------------------------------------------------------------- private methods
    /*
     * Generates rollback statements from the inverse changes returned by createInverses()
     *
     * @param database the target {@link Database} associated to this change's rollback statements
     * @return an array of {@link String}s containing the rollback statements from the inverse changes
     * @throws UnsupportedChangeException if this change is not supported by the {@link Database} passed as argument
     * @throws RollbackImpossibleException if rollback is not supported for this change
     */
    private SqlStatement[] generateRollbackStatementsFromInverse(Database database) throws UnsupportedChangeException, RollbackImpossibleException {
        Change[] inverses = createInverses();
        if (inverses == null) {
            throw new RollbackImpossibleException("No inverse to " + getClass().getName() + " created");
        }

        List<SqlStatement> statements = new ArrayList<SqlStatement>();

        for (Change inverse : inverses) {
            statements.addAll(Arrays.asList(inverse.generateStatements(database)));
        }

        return statements.toArray(new SqlStatement[statements.size()]);
    }

    /*
     * Create inverse changes that can roll back this change. This method is intended
     * to be overriden by the subclasses that can create inverses.
     *
     * @return an array of {@link Change}s containing the inverse
     *         changes that can roll back this change
     */
    protected Change[] createInverses() {
        return null;
    }

    /**
     * Default implementation that stores the file opener provided when the
     * Change was created.
     */
    public void setFileOpener(FileOpener fileOpener) {
        this.fileOpener = fileOpener;
    }
    
    /**
     * Returns the FileOpen as provided by the creating ChangeLog.
     * 
     * @return The file opener
     */
    public FileOpener getFileOpener() {
        return fileOpener;
    }
    
    /**
     * Most Changes don't need to do any setup.
     * This implements a no-op
     */
    public void setUp() throws SetupException {
        
    }

    public Set<DatabaseObject> getAffectedDatabaseObjects(Database database) {
        Set<DatabaseObject> affectedObjects = new HashSet<DatabaseObject>();
        try {
            for (SqlStatement statement : generateStatements(database)) {
                SqlGenerator sqlGenerator = SqlGeneratorFactory.getInstance().getBestGenerator(statement, database);
                if (sqlGenerator != null) {
                    for (Sql sql : sqlGenerator.generateSql(statement, database)) {
                        affectedObjects.addAll(sql.getAffectedDatabaseObjects());
                    }
                }

            }

            return affectedObjects;
        } catch (UnsupportedChangeException e) {
            return new HashSet<DatabaseObject>();
        }
    }

}
