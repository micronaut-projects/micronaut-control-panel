import {nodeResolve} from "@rollup/plugin-node-resolve"
import commonjs from "@rollup/plugin-commonjs"
export default {
    input: "./editor.mjs",
    output: {
        // Generate into the module build/resources so Micronaut can serve it from classpath:static/js
        file: "../../../build/generated/resources/main/static/js/editor.bundle.js",
        format: "iife",
        inlineDynamicImports: true
    },
    plugins: [
        commonjs(),
        nodeResolve()
    ]
}
