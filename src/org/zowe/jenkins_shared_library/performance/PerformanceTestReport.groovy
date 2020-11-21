/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.performance

import org.codehaus.groovy.runtime.InvokerHelper
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException

/**
 * Class to work with performance test report.
 *
 * @Example
 * <pre>
 *     def rpt = new PerformanceTestReport(this)
 *     rpt.generateCpuChart(testReport)
 * </pre>
 */
class PerformanceTestReport {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Reports folder to store html report
     *
     * @Default {@code 'reports'}
     */
    String reportDir = 'reports'

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def o = new PerformanceTestReport(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    PerformanceTestReport(steps) {
        this.steps = steps
    }

    /**
     * Return sanitized test name
     *
     * @param testReport     test report object of one test
     */
    String getSanitizedTestName(testReport) {
        if (!testReport || !testReport.name) {
            throw new InvalidArgumentException("Invalid test doesn't have a name")
        }

        return testReport.name.toLowerCase().replaceAll(/[^0-9a-zA-Z]/, "-")
    }

    /**
     * Generate CPU Time Chart
     *
     * @param reportFile     report yaml file
     * @return Map     contains: sanitizedName, html, js
     */
    void generateCpuChartHtmlReport(reportFile) throws InvalidArgumentException {
        def report = steps.readYaml(file: reportFile)
        if (!report || !report.tests) {
            throw new InvalidArgumentException("Invalid report doesn't have tests")
        }

        steps.echo "Processing report ${reportFile} ..."

        def htmlReport = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "  <script src=\"https://code.highcharts.com/highcharts.js\"></script>\n" +
            "  <script src=\"https://code.highcharts.com/modules/data.js\"></script>\n" +
            "  <script src=\"https://code.highcharts.com/modules/exporting.js\"></script>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>Test ${reportFile}</h1>\n"
        def jsReport = "document.addEventListener('DOMContentLoaded', function () {\n" +
            "var baseUrl = window.location.origin + window.location.pathname.substring(0, window.location.pathname.lastIndexOf('/'));\n"

        def testIndex = 0
        report.tests.each { test ->
            testIndex = testIndex + 1

            def cpuChart = this.generateCpuChartForOneTest(test)
            if (cpuChart) {
                htmlReport += cpuChart['html']
                jsReport += cpuChart['js']
            }
        }

        if (testIndex > 0) {
            // write test report
            this.echo "Saving HTML report"
            htmlReport += "<script src=\"main.js\"></script>\n" +
                "</body>\n" +
                "</html>"
            steps.writeFile file: "${this.reportDir}/index.html", text: htmlReport
            jsReport += "});"
            steps.writeFile file: "${this.reportDir}/main.js", text: jsReport
            steps.publishHTML([
                allowMissing          : false,
                alwaysLinkToLastBuild : true,
                keepAll               : true,
                reportDir             : this.reportDir,
                reportFiles           : 'index.html',
                reportName            : 'Test Result Charts',
                reportTitles          : ''
            ])
        }
    }

    /**
     * Generate CPU Time Chart
     *
     * @param testReport     test report object of one test
     * @return Map     contains: sanitizedName, html, js
     */
    Map generateCpuChartForOneTest(testReport) throws InvalidArgumentException {
        if (!testReport || !testReport.name) {
            throw new InvalidArgumentException("Invalid test doesn't have a name")
        }
        steps.echo "Generating CPU Time Chart for test ${test.name} ..."
        if (!testReport["serverMetrics"] || testReport["serverMetrics"].size() == 0) {
            steps.echo "- no server side metrics found"
            return [:]
        }

        // what metric we want to display
        def regex = /^cpu\{.+,(?:item|process)="([^"]+)",(?:jobid|extra)="([^"]+)"\}$/

        Map result = [:]
        result['sanitizedName'] = this.getSanitizedTestName(testReport)
        // csv file name used by the chart
        def csvFile = result['sanitizedName'] + "-cpu.csv"

        steps.echo "- Preparing CSV ${csvFile} ..."

        // prepare columns and row indexes (timestamp)
        def csvColumns = []
        def csvRowIndexes = []
        testReport.serverMetrics.each { metric ->
            def matches = metric.name =~ regex
            if (matches.matches() && matches[0] && matches[0].size() == 3) {
                def process = matches[0][1]
                def extra = matches[0][2]
                def colname = "${process}${extra ? "(${extra})" : ""}"
                if (!csvColumns.contains(colname)) {
                    csvColumns.add(colname)
                }

                if (!csvRowIndexes.contains(metric.timestamp)) {
                    csvRowIndexes.add(metric.timestamp)
                }
            }
        }
        csvColumns.sort().add(0, "Timestamp")
        csvRowIndexes.sort()

        // create map because indexOf requires special privileges
        def totalCols = csvColumns.size()
        def totalRows = csvRowIndexes.size()
        def csvColumnsMap = [:]
        def csvRowIndexesMap = [:]
        for (i = 0; i < totalCols; i++) {
            csvColumnsMap["${csvColumns[i]}".toString()] = i
        }
        for (i = 0; i < totalRows; i++) {
            csvRowIndexesMap["${csvRowIndexes[i]}".toString()] = i
        }

        // init result rows
        def csvRows = []
        csvRowIndexes.each { ts ->
            def row = []
            for (i = 0; i < totalCols; i++) {
                row.add(i == 0 ? ts : null)
            }
            csvRows.add(row)
        }

        // fill in data
        testReport.serverMetrics.each { metric ->
            def matches = metric.name =~ regex
            if (matches.matches() && matches[0] && matches[0].size() == 3) {
                def process = matches[0][1]
                def extra = matches[0][2]
                def colname = "${process}${extra ? "(${extra})" : ""}"
                def col = csvColumnsMap[colname]
                def row = csvRowIndexesMap["${metric.timestamp}".toString()]
                csvRows[row][col] = metric.value
            }
        }

        // calculate delta
        for (i = csvRows.size() - 1; i >= 0; i--) {
            for (j = 1; j < csvRows[i].size(); j++) {
                if (i > 0) {
                    csvRows[i][j] -= csvRows[i - 1][j]
                } else {
                    csvRows[i][j] = 0
                }
            }
        }

        def csvContent = []
        csvContent.add("\"" + csvColumns.join("\",\"") + "\"")
        csvRows.each {
            csvContent.add(it.join(","))
        }
        steps.echo csvContent.join("\n")
        steps.writeFile file: "${csvFile}", text: csvContent.join("\n")

        // html report
        result['html'] = "<h2>${testReport.name}</h2>\n" +
            "<ul>\n"
        if (testReport.result && testReport.result.total_cpu_percentage_from_server_metrics) {
            result['html'] += "<li><strong>Test Duration<strong>: ${testReport.result.total_time_elapse_from_server_metrics}</li>\n"
            result['html'] += "<li><strong>Total CPU Time<strong>: ${testReport.result.total_cpu_time_from_server_metrics}</li>\n"
            result['html'] += "<li><strong>Average CPU %<strong>: ${testReport.result.total_cpu_percentage_from_server_metrics}%</li>\n"
        }
        result['html'] += "</ul>\n"

        // highcharts chart html container
        result['html'] += "\n" +
            "<div id=\"container_${result['sanitizedName']}_cpu\" style=\"width:80%; height:400px; margin: auto;\"></div>\n"

        // highcharts script to generate chart for this test
        result['js'] = "\n" +
            "  Highcharts.chart('container_${result['sanitizedName']}_cpu', {\n" +
            "    chart: { type: 'line' },\n" +
            "    data: { csvURL: baseUrl + '/${csvFile}' },\n" +
            "    title: { text: '${testReport.name}' },\n" +
            "    yAxis: { title: { text: 'CPU Time Delta' } }\n" +
            "  });\n"

        // result should contain: sanitizedName, html, js
        return result
    }

    /**
     * Update plot
     *
     * @param reportFile     report yaml file
     */
    void updatePlot(reportFile) throws InvalidArgumentException {
        def report = steps.readYaml(file: reportFile)
        if (!report || !report.tests) {
            throw new InvalidArgumentException("Invalid report doesn't have tests")
        }

        steps.echo "Updating plot for ${reportFile} ..."

        report.tests.each { test ->
            this.updatePlotForOneTest(test)
        }
    }


    /**
     * Update plot CSV for one test
     *
     * @param testReport     test report object of one test
     */
    void updatePlotForOneTest(testReport) {
        if (!testReport || !testReport.name) {
            throw new InvalidArgumentException("Invalid test doesn't have a name")
        }
        steps.echo "Updating CPU Time plot for test ${testReport.name} ..."
        if (!testReport["serverMetrics"] || testReport["serverMetrics"].size() == 0) {
            steps.echo "- no server side metrics found"
            return
        }

        def sanitizedTestName = this.getSanitizedTestName(testReport)
        def plotFile
        def plotCsvContent

        // update cpu plot
        if (testReport.result && testReport.result.total_cpu_percentage_from_server_metrics) {
            plotFile = "plot-" + sanitizedTestName + "-cpu-build.csv"
            plotCsvContent = []
            plotCsvContent.push('"Build","Average CPU %","Total CPU Time","Duration"')
            plotCsvContent.push("${BUILD_NUMBER},${testReport.result.total_cpu_percentage_from_server_metrics},${testReport.result.total_cpu_time_from_server_metrics},${testReport.result.total_time_elapse_from_server_metrics}")

            // write plot file
            steps.echo "- writing ${plotFile} ..."
            steps.echo plotCsvContent.join("\n")
            steps.writeFile file: "${this.reportDir}/${plotFile}", text: plotCsvContent.join("\n")

            // generate plot graph
            steps.plot csvFileName: "plot-" + sanitizedTestName + "-cpu-global.csv",
                csvSeries: [[
                    displayTableFlag: true,
                    file: "${this.reportDir}/${plotFile}",
                    inclusionFlag: 'INCLUDE_BY_STRING',
                    exclusionValues: 'Average CPU %'
                ]],
                group: 'Performance Dashoard',
                numBuilds: '20',
                style: 'line',
                title: "${testReport.name}",
                yaxis: 'CPU %'
        }

        // update request-per-second plot
        if (testReport.result && testReport.result.requests_per_second) {
            plotFile = "plot-" + sanitizedTestName + "-rps-build.csv"
            plotCsvContent = []
            plotCsvContent.push('"Build","Request per Second"')
            plotCsvContent.push("${BUILD_NUMBER},${testReport.result.requests_per_second}")

            // write plot file
            steps.echo "- writing ${plotFile} ..."
            steps.echo plotCsvContent.join("\n")
            steps.writeFile file: "${this.reportDir}/${plotFile}", text: plotCsvContent.join("\n")

            // generate plot graph
            steps.plot csvFileName: "plot-" + sanitizedTestName + "-rps-global.csv",
                csvSeries: [[
                    displayTableFlag: true,
                    file: "${this.reportDir}/${plotFile}",
                    inclusionFlag: 'INCLUDE_BY_STRING',
                    exclusionValues: 'Request per Second'
                ]],
                group: 'Performance Dashoard',
                numBuilds: '20',
                style: 'line',
                title: "${testReport.name}",
                yaxis: 'Requests/s'
        }
    }
}
