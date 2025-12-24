import {EditorView, showPanel} from "@codemirror/view";
import {EditorState} from "@codemirror/state";
import {basicSetup} from "codemirror";
import {sql, StandardSQL, PostgreSQL, MySQL, MariaSQL, MSSQL, PLSQL} from "@codemirror/lang-sql"

function getSQLExtensions(databaseType, schemaConfig) {
    let dbDialect;
    switch (databaseType.toUpperCase()) {
        case 'MYSQL':
            dbDialect = MySQL;
            break;
        case 'MARIADB':
            dbDialect = MariaSQL;
            break;
        case 'POSTGRES':
            dbDialect = PostgreSQL;
            break;
        case 'SQL_SERVER':
            dbDialect = MSSQL;
            break;
        case 'ORACLE':
            dbDialect = PLSQL;
            break;
        case 'GENERIC':
        default:
            dbDialect = StandardSQL;
            break;
    }
    return [
        basicSetup,
        sql({
            dialect: dbDialect,
            upperCaseKeywords: true,
            schema: (schemaConfig && schemaConfig.schema) ? schemaConfig.schema : undefined,
            defaultSchema: (schemaConfig && schemaConfig.defaultSchema) ? schemaConfig.defaultSchema : undefined,
        })
    ];
}

window.codemirror = {
    EditorState,
    EditorView,
    showPanel,
    getSQLExtensions
};
