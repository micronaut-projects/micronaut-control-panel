import {EditorView, showPanel} from "@codemirror/view";
import {EditorState} from "@codemirror/state";
import {HighlightStyle, syntaxHighlighting} from "@codemirror/language";
import {tags} from "@lezer/highlight";
import {basicSetup} from "codemirror";
import {sql, StandardSQL, PostgreSQL, MySQL, MariaSQL, MSSQL, PLSQL} from "@codemirror/lang-sql"
import { sqlExtension, cteCompletionSource } from "@marimo-team/codemirror-sql"

const sqlHighlightStyle = HighlightStyle.define([
    { tag: [tags.keyword, tags.operatorKeyword], color: "var(--sql-keyword)", fontWeight: "600" },
    { tag: [tags.atom, tags.bool], color: "var(--sql-atom)" },
    { tag: [tags.string, tags.special(tags.string)], color: "var(--sql-string)" },
    { tag: [tags.number, tags.integer, tags.float], color: "var(--sql-number)" },
    { tag: [tags.comment, tags.lineComment, tags.blockComment], color: "var(--sql-comment)", fontStyle: "italic" },
    { tag: [tags.name, tags.variableName, tags.definition(tags.name), tags.propertyName], color: "var(--sql-name)" },
    { tag: [tags.function(tags.variableName), tags.function(tags.propertyName)], color: "var(--sql-function)" },
    { tag: [tags.operator, tags.punctuation, tags.separator], color: "var(--sql-punctuation)" },
    { tag: tags.invalid, color: "var(--destructive)" }
]);

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
        syntaxHighlighting(sqlHighlightStyle),
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
