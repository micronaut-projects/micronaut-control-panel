import {nodeResolve} from "@rollup/plugin-node-resolve"
import commonjs from "@rollup/plugin-commonjs"
export default {
    input: "./editor.mjs",
    output: {
        file: "../resources/static/js/editor.bundle.js",
        format: "iife",
        inlineDynamicImports: true
    },
    plugins: [
        commonjs(),
        nodeResolve()
    ]
}
