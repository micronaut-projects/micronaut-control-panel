import {EditorView} from "@codemirror/view";
import {EditorState} from "@codemirror/state";
import {basicSetup} from "codemirror";
import {sql, StandardSQL} from "@codemirror/lang-sql"

const extensions = [
    basicSetup,
    sql({
        dialect: StandardSQL,
        upperCaseKeywords: true,
    })
]

const state = EditorState.create({
    doc: "/* Type an SQL query here, or select a table from the tree */\n\n",
    extensions: extensions
});

const view = new EditorView({
    state,
    parent: document.querySelector("#sql-console")
});

window.codemirror = {
    EditorState,
    EditorView,
    state,
    view,
    extensions
};
