import {EditorView, showPanel} from "@codemirror/view";
import {EditorState} from "@codemirror/state";
import {basicSetup} from "codemirror";
import {sql, StandardSQL, PostgreSQL, MySQL, MariaSQL, MSSQL, PLSQL} from "@codemirror/lang-sql"
import { sqlExtension, cteCompletionSource } from "@marimo-team/codemirror-sql"


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
    const schema = (schemaConfig && schemaConfig.schema) ? schemaConfig.schema : undefined;
    const defaultSchema = (schemaConfig && schemaConfig.defaultSchema) ? schemaConfig.defaultSchema : undefined;
    return [
        basicSetup,
        sql({
            dialect: dbDialect,
            upperCaseKeywords: true,
            schema: schema,
            defaultSchema: defaultSchema,
        }),
        dbDialect.language.data.of({
            autocomplete: cteCompletionSource,
        }),
        sqlExtension({
            linterConfig: {
                delay: 250,
            },
            gutterConfig: {
                backgroundColor: "#3b82f6",
                errorBackgroundColor: "#ef4444",
                hideWhenNotFocused: true,
            },
            enableHover: false
        })
    ];
}

window.codemirror = {
    EditorState,
    EditorView,
    showPanel,
    getSQLExtensions
};
