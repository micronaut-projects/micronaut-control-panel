import {EditorView} from "@codemirror/view";
import {EditorState} from "@codemirror/state";
import {basicSetup} from "codemirror";
import {sql, StandardSQL, PostgreSQL, MySQL, MariaSQL, MSSQL} from "@codemirror/lang-sql"

function getSQLExtensions(databaseType) {
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
        })
    ];
}

window.codemirror = {
    EditorState,
    EditorView,
    getSQLExtensions
};
