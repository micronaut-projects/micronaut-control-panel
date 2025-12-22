import {EditorView, basicSetup} from "codemirror"
import {sql, StandardSQL} from "@codemirror/lang-sql"

let editor = new EditorView({
    doc: "/* Type an SQL query here, or select a table from the tree */\n\n",
    extensions: [
        basicSetup,
        sql({
            dialect: StandardSQL,
            upperCaseKeywords: true,
        }),
    ],
    parent: document.querySelector("#sql-console")
})
