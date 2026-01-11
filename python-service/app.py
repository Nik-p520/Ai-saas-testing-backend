"""
Production-Ready AI Playwright Testing Service v6.0
===================================================
✅ Genuine bug detection with evidence
✅ Real performance metrics
✅ Accurate health scoring
✅ Screenshot-backed findings
"""

import os
import json
import time
import base64
import traceback
import asyncio
import logging
from datetime import datetime
from typing import Dict, List, Optional

from flask import Flask, request, jsonify
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeoutError
import google.generativeai as genai
from concurrent.futures import ThreadPoolExecutor
from dotenv import load_dotenv

# =============================================================================
# CONFIGURATION
# =============================================================================

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("AI_Tester")

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    raise ValueError("GEMINI_API_KEY is missing in environment.")

GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
genai.configure(api_key=GEMINI_API_KEY)

app = Flask(__name__)

# =============================================================================
# GENUINE BUG DETECTOR
# =============================================================================

class GenuineBugDetector:
    """Detects only real, verifiable bugs and issues"""

    def __init__(self):
        self.bugs = []
        self.findings = {
            "critical": [],
            "high": [],
            "medium": [],
            "low": [],
            "info": []
        }
        self.metrics = {}

    def analyze(self, data: Dict) -> Dict:
        """Analyze test data for genuine issues"""
        logger.info("🔍 Starting genuine bug detection analysis...")

        # Extract data safely
        load_time = data.get("load_time", 0.0)
        console_logs = data.get("console_logs", [])
        network_logs = data.get("network_logs", [])
        errors = data.get("errors", [])
        page_data = data.get("page_data", {})
        perf_metrics = data.get("performance_metrics", {})

        # Run all detection methods
        self._detect_javascript_errors(console_logs)
        self._detect_network_failures(network_logs)
        self._detect_runtime_errors(errors)
        self._detect_performance_issues(load_time, perf_metrics)
        self._detect_ui_bugs(page_data)
        self._detect_seo_issues(page_data)
        self._detect_security_issues(data.get("url", ""))

        # Calculate scores
        scores = self._calculate_health_scores(load_time)

        # Generate recommendations
        recommendations = self._generate_recommendations()

        logger.info(f"✅ Analysis complete: {len(self.bugs)} bugs found, Score: {scores['overall']}")

        return {
            "bugs": self.bugs,
            "findings": self.findings,
            "scores": scores,
            "metrics": self.metrics,
            "recommendations": recommendations
        }

    def _detect_javascript_errors(self, console_logs: List[Dict]):
        """Detect actual JavaScript errors in console"""
        errors = [log for log in console_logs if log.get("type") == "error"]
        warnings = [log for log in console_logs if log.get("type") == "warning"]

        self.metrics["console_errors"] = len(errors)
        self.metrics["console_warnings"] = len(warnings)

        # Categorize errors
        for err in errors:
            text = err.get("text", "")

            # Identify error type
            if "TypeError" in text:
                error_type = "Type Error"
                severity = "HIGH"
            elif "ReferenceError" in text:
                error_type = "Reference Error"
                severity = "HIGH"
            elif "SyntaxError" in text:
                error_type = "Syntax Error"
                severity = "CRITICAL"
            elif "404" in text or "Failed to load" in text:
                error_type = "Resource Load Error"
                severity = "MEDIUM"
            else:
                error_type = "JavaScript Error"
                severity = "MEDIUM"

            self.bugs.append({
                "id": f"JS-{len(self.bugs) + 1}",
                "type": error_type,
                "severity": severity,
                "message": text[:200],
                "impact": "May cause broken functionality or crashes",
                "location": "Browser Console"
            })

            if severity == "CRITICAL" or severity == "HIGH":
                self.findings["critical"].append({
                    "title": error_type,
                    "description": text[:250],
                    "fix": "Debug and fix the JavaScript error"
                })

        # Handle warnings
        if warnings:
            deprecated = [w for w in warnings if "deprecated" in w.get("text", "").lower()]
            if deprecated:
                self.findings["medium"].append({
                    "title": f"{len(deprecated)} Deprecation Warnings",
                    "description": "Using deprecated APIs or features",
                    "fix": "Update to modern alternatives"
                })

    def _detect_network_failures(self, network_logs: List[Dict]):
        """Detect failed network requests"""
        # Filter out third-party noise
        third_party_keywords = [
            "analytics", "doubleclick", "googlesyndication",
            "googletagmanager", "facebook.com", "google-analytics"
        ]

        filtered_logs = [
            log for log in network_logs
            if not any(kw in log.get("url", "").lower() for kw in third_party_keywords)
        ]

        # Detect failures
        failed_4xx = [r for r in filtered_logs if 400 <= r.get("status", 200) < 500]
        failed_5xx = [r for r in filtered_logs if r.get("status", 200) >= 500]

        self.metrics["network_4xx_errors"] = len(failed_4xx)
        self.metrics["network_5xx_errors"] = len(failed_5xx)
        self.metrics["total_network_requests"] = len(network_logs)

        # 4xx errors (client errors)
        for req in failed_4xx:
            self.bugs.append({
                "id": f"NET-{len(self.bugs) + 1}",
                "type": "Resource Not Found",
                "severity": "HIGH",
                "message": f"{req.get('status')} error loading resource",
                "impact": "Missing images, scripts, or API data",
                "url": req.get("url", "")[:150]
            })

            self.findings["critical"].append({
                "title": f"{req.get('status')} Error",
                "description": f"Failed to load: {req.get('url', '')[:120]}",
                "fix": "Fix the URL or ensure the resource exists"
            })

        # 5xx errors (server errors)
        for req in failed_5xx:
            self.bugs.append({
                "id": f"NET-{len(self.bugs) + 1}",
                "type": "Server Error",
                "severity": "CRITICAL",
                "message": f"{req.get('status')} server error",
                "impact": "Backend API or service failure",
                "url": req.get("url", "")[:150]
            })

            self.findings["critical"].append({
                "title": f"{req.get('status')} Server Error",
                "description": f"Server failed: {req.get('url', '')[:120]}",
                "fix": "Fix backend server issues"
            })

    def _detect_runtime_errors(self, errors: List[Dict]):
        """Detect page-level runtime errors"""
        self.metrics["runtime_errors"] = len(errors)

        for err in errors:
            error_text = err.get("error", "")

            self.bugs.append({
                "id": f"ERR-{len(self.bugs) + 1}",
                "type": "Unhandled Exception",
                "severity": "CRITICAL",
                "message": error_text[:200],
                "impact": "Application crash or broken features"
            })

            self.findings["critical"].append({
                "title": "Unhandled Runtime Error",
                "description": error_text[:250],
                "fix": "Add error handling and fix the root cause"
            })

    def _detect_performance_issues(self, load_time: float, perf_metrics: Dict):
        """Detect performance problems"""
        self.metrics["load_time_seconds"] = load_time
        self.metrics["performance_metrics"] = perf_metrics

        # Time to First Byte
        ttfb = perf_metrics.get("ttfb", 0)

        if ttfb > 600:
            self.bugs.append({
                "id": f"PERF-{len(self.bugs) + 1}",
                "type": "Slow Server Response",
                "severity": "MEDIUM",
                "message": f"Time to First Byte: {ttfb}ms (threshold: 600ms)",
                "impact": "Delayed initial page rendering",
                "metric": f"{ttfb}ms"
            })

            self.findings["high"].append({
                "title": "Slow Server Response Time",
                "description": f"TTFB is {ttfb}ms (should be <600ms)",
                "fix": "Optimize server processing, use caching, CDN"
            })

        # Overall load time
        if load_time > 8.0:
            self.bugs.append({
                "id": f"PERF-{len(self.bugs) + 1}",
                "type": "Critically Slow Load Time",
                "severity": "CRITICAL",
                "message": f"Page load: {load_time:.2f}s (threshold: 8s)",
                "impact": "Users will abandon the page",
                "metric": f"{load_time:.2f}s"
            })

            self.findings["critical"].append({
                "title": "Unacceptable Load Time",
                "description": f"Page took {load_time:.2f} seconds to load",
                "fix": "Optimize images, enable compression, reduce bundle size"
            })
        elif load_time > 5.0:
            self.findings["high"].append({
                "title": "Slow Load Time",
                "description": f"Page loaded in {load_time:.2f}s (target: <5s)",
                "fix": "Implement lazy loading, optimize assets"
            })
        elif load_time > 3.0:
            self.findings["medium"].append({
                "title": "Moderate Load Time",
                "description": f"Page loaded in {load_time:.2f}s (optimal: <3s)",
                "fix": "Further optimize for better performance"
            })
        else:
            self.findings["info"].append({
                "title": "Good Performance",
                "description": f"Page loaded in {load_time:.2f}s - within optimal range"
            })

    def _detect_ui_bugs(self, page_data: Dict):
        """Detect UI/UX issues"""
        broken_images = page_data.get("broken_images", 0)
        empty_buttons = page_data.get("empty_buttons", 0)
        images_without_alt = page_data.get("images_without_alt", 0)
        unlabeled_inputs = page_data.get("unlabeled_inputs", 0)

        self.metrics["ui_stats"] = {
            "total_buttons": page_data.get("buttons", 0),
            "total_links": page_data.get("links", 0),
            "total_images": page_data.get("images", 0),
            "total_forms": page_data.get("forms", 0)
        }

        # Broken images
        if broken_images > 0:
            self.bugs.append({
                "id": f"UI-{len(self.bugs) + 1}",
                "type": "Broken Images",
                "severity": "MEDIUM",
                "message": f"{broken_images} images failed to load",
                "impact": "Missing visual content, poor UX",
                "count": broken_images
            })

            self.findings["high"].append({
                "title": f"{broken_images} Broken Images",
                "description": "Images with invalid src or loading failures",
                "fix": "Verify image URLs and ensure hosting is working"
            })

        # Empty buttons (accessibility issue)
        if empty_buttons > 0:
            self.bugs.append({
                "id": f"A11Y-{len(self.bugs) + 1}",
                "type": "Empty Buttons",
                "severity": "MEDIUM",
                "message": f"{empty_buttons} buttons without text or labels",
                "impact": "Inaccessible to screen readers",
                "count": empty_buttons
            })

            self.findings["medium"].append({
                "title": f"{empty_buttons} Unlabeled Buttons",
                "description": "Buttons missing text or aria-label",
                "fix": "Add descriptive text or aria-label attributes"
            })

        # Images without alt text
        if images_without_alt > 0:
            self.bugs.append({
                "id": f"A11Y-{len(self.bugs) + 1}",
                "type": "Missing Alt Text",
                "severity": "MEDIUM",
                "message": f"{images_without_alt} images without alt attributes",
                "impact": "Inaccessible to screen readers, poor SEO",
                "count": images_without_alt
            })

            self.findings["medium"].append({
                "title": "Images Missing Alt Text",
                "description": f"{images_without_alt} images lack alternative text",
                "fix": "Add descriptive alt attributes to all images"
            })

        # Form inputs without labels
        if unlabeled_inputs > 0:
            self.bugs.append({
                "id": f"A11Y-{len(self.bugs) + 1}",
                "type": "Unlabeled Form Inputs",
                "severity": "MEDIUM",
                "message": f"{unlabeled_inputs} form inputs without labels",
                "impact": "Screen readers cannot identify fields",
                "count": unlabeled_inputs
            })

            self.findings["medium"].append({
                "title": "Form Accessibility Issue",
                "description": f"{unlabeled_inputs} inputs missing labels",
                "fix": "Add <label> elements or aria-label attributes"
            })

    def _detect_seo_issues(self, page_data: Dict):
        """Detect SEO problems"""
        has_title = page_data.get("has_title", False)
        title = page_data.get("title", "")
        has_meta_desc = page_data.get("has_meta_description", False)
        has_h1 = page_data.get("has_h1", False)
        h1_count = page_data.get("h1_count", 0)

        self.metrics["seo_checks"] = {
            "has_title": has_title,
            "title_length": len(title),
            "has_meta_description": has_meta_desc,
            "has_h1": has_h1,
            "h1_count": h1_count
        }

        # Missing title tag
        if not has_title or len(title) == 0:
            self.bugs.append({
                "id": f"SEO-{len(self.bugs) + 1}",
                "type": "Missing Page Title",
                "severity": "HIGH",
                "message": "Page has no <title> element",
                "impact": "Poor SEO, missing browser tab text"
            })

            self.findings["critical"].append({
                "title": "Missing Page Title",
                "description": "No <title> tag found in <head>",
                "fix": "Add a descriptive title tag (50-60 characters)"
            })
        elif len(title) < 30:
            self.findings["medium"].append({
                "title": "Title Too Short",
                "description": f"Title is {len(title)} chars (recommend 50-60)",
                "fix": "Write a more descriptive title"
            })

        # Missing meta description
        if not has_meta_desc:
            self.findings["medium"].append({
                "title": "Missing Meta Description",
                "description": "No meta description tag found",
                "fix": "Add meta description (150-160 characters)"
            })

        # Missing or multiple H1
        if not has_h1:
            self.findings["medium"].append({
                "title": "Missing H1 Heading",
                "description": "No H1 tag found on page",
                "fix": "Add one H1 tag describing main content"
            })
        elif h1_count > 1:
            self.findings["low"].append({
                "title": "Multiple H1 Tags",
                "description": f"Found {h1_count} H1 tags (should be 1)",
                "fix": "Use only one H1 per page"
            })

    def _detect_security_issues(self, url: str):
        """Detect security vulnerabilities"""
        is_https = url.startswith("https://")

        self.metrics["security_checks"] = {
            "is_https": is_https
        }

        if not is_https:
            self.bugs.append({
                "id": f"SEC-{len(self.bugs) + 1}",
                "type": "Insecure Connection",
                "severity": "CRITICAL",
                "message": "Site using HTTP instead of HTTPS",
                "impact": "Data not encrypted, vulnerable to attacks"
            })

            self.findings["critical"].append({
                "title": "No HTTPS Encryption",
                "description": "Site accessible via insecure HTTP protocol",
                "fix": "Install SSL certificate and enforce HTTPS redirects"
            })

    def _calculate_health_scores(self, load_time: float) -> Dict:
        """Calculate comprehensive health scores"""

        # Performance Score (0-100)
        if load_time <= 1.5:
            perf_score = 100
        elif load_time <= 3.0:
            perf_score = 85
        elif load_time <= 5.0:
            perf_score = 70
        elif load_time <= 8.0:
            perf_score = 50
        else:
            perf_score = 20

        # Stability Score (0-100)
        stability = 100
        stability -= min(50, self.metrics.get("console_errors", 0) * 15)
        stability -= min(40, self.metrics.get("runtime_errors", 0) * 20)
        stability -= min(30, self.metrics.get("network_4xx_errors", 0) * 10)
        stability -= min(40, self.metrics.get("network_5xx_errors", 0) * 20)
        stability = max(0, int(stability))

        # Bug count penalty
        critical_bugs = len([b for b in self.bugs if b["severity"] == "CRITICAL"])
        high_bugs = len([b for b in self.bugs if b["severity"] == "HIGH"])
        bug_penalty = (critical_bugs * 10) + (high_bugs * 5)

        # Overall score
        overall = int((perf_score * 0.5) + (stability * 0.5))
        overall = max(0, min(100, overall - bug_penalty))

        # Determine grade
        if overall >= 90:
            grade = "A"
        elif overall >= 80:
            grade = "B"
        elif overall >= 70:
            grade = "C"
        elif overall >= 60:
            grade = "D"
        else:
            grade = "F"

        # Pass/Fail status
        status = "PASS" if overall >= 70 and critical_bugs == 0 else "FAIL"

        return {
            "performance": int(perf_score),
            "stability": stability,
            "overall": overall,
            "grade": grade,
            "status": status,
            "bug_count": len(self.bugs),
            "critical_bugs": critical_bugs,
            "high_bugs": high_bugs
        }

    def _generate_recommendations(self) -> List[Dict]:
        """Generate actionable recommendations based on findings"""
        recs = []

        # Critical bugs
        critical_bugs = [b for b in self.bugs if b["severity"] == "CRITICAL"]
        if critical_bugs:
            recs.append({
                "priority": "CRITICAL",
                "title": "Fix Critical Bugs Immediately",
                "description": f"{len(critical_bugs)} critical issues blocking production",
                "actions": [f"{b['type']}: {b['message']}" for b in critical_bugs[:5]]
            })

        # Performance issues
        if self.metrics.get("load_time_seconds", 0) > 3:
            recs.append({
                "priority": "HIGH",
                "title": "Optimize Page Performance",
                "description": "Page load time exceeds recommended threshold",
                "actions": [
                    "Compress and optimize images (use WebP format)",
                    "Enable Gzip/Brotli compression",
                    "Minify CSS and JavaScript files",
                    "Implement lazy loading for images",
                    "Use a CDN for static assets",
                    "Reduce JavaScript bundle size"
                ]
            })

        # JavaScript errors
        if self.metrics.get("console_errors", 0) > 0:
            recs.append({
                "priority": "HIGH",
                "title": "Resolve JavaScript Errors",
                "description": f"{self.metrics['console_errors']} console errors detected",
                "actions": [
                    "Review browser console for error stack traces",
                    "Fix undefined variables and type errors",
                    "Add proper error boundaries",
                    "Implement comprehensive error handling",
                    "Test in multiple browsers"
                ]
            })

        # Network failures
        if self.metrics.get("network_4xx_errors", 0) > 0 or self.metrics.get("network_5xx_errors", 0) > 0:
            recs.append({
                "priority": "HIGH",
                "title": "Fix Network Request Failures",
                "description": "Multiple failed network requests detected",
                "actions": [
                    "Fix 404 errors by updating resource URLs",
                    "Resolve server-side 500 errors",
                    "Implement retry logic for failed requests",
                    "Add fallback content for missing resources"
                ]
            })

        # SEO issues
        seo = self.metrics.get("seo_checks", {})
        if not seo.get("has_title") or not seo.get("has_meta_description"):
            recs.append({
                "priority": "MEDIUM",
                "title": "Improve SEO Fundamentals",
                "description": "Missing critical SEO elements",
                "actions": [
                    "Add unique, descriptive title tags (50-60 characters)",
                    "Write compelling meta descriptions (150-160 characters)",
                    "Use proper heading hierarchy (one H1 per page)",
                    "Add structured data markup",
                    "Optimize URL structure"
                ]
            })

        return recs


# =============================================================================
# PLAYWRIGHT TESTER
# =============================================================================

class PlaywrightTester:
    """Execute browser tests using Playwright"""

    async def execute(self, url: str) -> Dict:
        """Run comprehensive browser test"""
        start_time = time.time()
        logger.info(f"🎬 Starting test for: {url}")

        screenshots = []
        console_logs = []
        network_logs = []
        errors = []

        try:
            async with async_playwright() as p:
                browser = await p.chromium.launch(headless=True)
                context = await browser.new_context(
                    viewport={"width": 1920, "height": 1080}
                )
                page = await context.new_page()

                # Set up event listeners
                page.on("console", lambda msg: console_logs.append({
                    "type": msg.type,
                    "text": msg.text[:500]
                }))

                page.on("pageerror", lambda error: errors.append({
                    "error": str(error)[:500]
                }))

                page.on("response", lambda response: network_logs.append({
                    "url": response.url,
                    "status": response.status,
                    "method": response.request.method
                }))

                # Navigate to page
                await page.goto(url, wait_until="networkidle", timeout=60000)
                await page.wait_for_timeout(2000)

                load_time = round(time.time() - start_time, 2)

                # Take screenshot
                screenshot_bytes = await page.screenshot(full_page=True)
                screenshots.append(base64.b64encode(screenshot_bytes).decode("utf-8"))

                # Collect page data
                page_data = await page.evaluate("""
    () => {
        const imgs = Array.from(document.querySelectorAll('img'));
        const btns = Array.from(document.querySelectorAll('button'));
        const inputs = Array.from(document.querySelectorAll('input, textarea, select'));
        
        return {
            buttons: btns.length,
            empty_buttons: btns.filter(b => 
                !b.textContent.trim() && 
                !b.getAttribute('aria-label') && 
                !b.title
            ).length,
            links: document.querySelectorAll('a[href]').length,
            forms: document.querySelectorAll('form').length,
            images: imgs.length,
            broken_images: imgs.filter(i => 
                !i.complete || i.naturalHeight === 0
            ).length,
            images_without_alt: imgs.filter(i => !i.hasAttribute("alt") || !i.alt).length,
            inputs: inputs.length,
            unlabeled_inputs: inputs.filter(i => 
                (!i.labels || i.labels.length === 0) && 
                !i.getAttribute('aria-label') &&
                !i.title
            ).length,
            has_title: !!document.title,
            title: document.title || "",
            has_meta_description: !!document.querySelector('meta[name="description"]'),
            has_h1: !!document.querySelector('h1'),
            h1_count: document.querySelectorAll('h1').length
        };
    }
""")


                # Get performance metrics
                performance_metrics = await page.evaluate("""
                    () => {
                        const p = performance.timing;
                        return {
                            ttfb: p.responseStart - p.requestStart,
                            dom_content_loaded: p.domContentLoadedEventEnd - p.navigationStart,
                            load_complete: p.loadEventEnd - p.navigationStart
                        };
                    }
                """)

                title = await page.title()

                await browser.close()

                logger.info(f"✅ Test completed in {load_time}s")

                return {
                    "success": True,
                    "url": url,
                    "title": title,
                    "load_time": load_time,
                    "page_data": page_data,
                    "console_logs": console_logs,
                    "network_logs": network_logs,
                    "errors": errors,
                    "performance_metrics": performance_metrics,
                    "screenshots": screenshots
                }

        except PlaywrightTimeoutError:
            logger.error("Timeout loading page")
            return {
                "success": False,
                "error": "Timeout: Page took too long to load (>60s)"
            }
        except Exception as e:
            logger.error(f"Test failed: {str(e)}")
            return {
                "success": False,
                "error": str(e),
                "traceback": traceback.format_exc()
            }


# =============================================================================
# AI ANALYST
# =============================================================================

class AIAnalyst:
    """Generate AI-powered summary using Gemini"""

    def __init__(self):
        self.model = genai.GenerativeModel(GEMINI_MODEL)
        self.executor = ThreadPoolExecutor(max_workers=2)

    async def summarize(self, analysis: Dict, test_data: Dict) -> Dict:
        """Generate executive summary"""
        logger.info("🧠 Generating AI summary...")

        prompt = f"""You are a QA expert analyzing website test results.

Test Results:
- URL: {test_data.get('url')}
- Load Time: {test_data.get('load_time')}s
- Bugs Found: {len(analysis['bugs'])}
- Health Score: {analysis['scores']['overall']}/100
- Grade: {analysis['scores']['grade']}

Critical Issues: {analysis['scores'].get('critical_bugs', 0)}
High Priority Issues: {analysis['scores'].get('high_bugs', 0)}

Top Findings:
{json.dumps(analysis['findings'], indent=2)[:1000]}

Provide a JSON response with:
- executive_summary: Brief 2-3 sentence overview
- key_insights: Array of 3 most important findings
- priority_actions: Array of top 3 recommended fixes

Focus only on actual detected issues. Be concise and actionable."""

        try:
            loop = asyncio.get_event_loop()
            response = await loop.run_in_executor(
                self.executor,
                lambda: self.model.generate_content(prompt)
            )

            text = response.text.strip()
            # Clean JSON formatting
            text = text.replace("```json", "").replace("```", "").strip()

            return json.loads(text)

        except Exception as e:
            logger.warning(f"AI summary generation failed: {e}")
            # Fallback summary
            return {
                "executive_summary": f"Found {len(analysis['bugs'])} genuine bugs with an overall health score of {analysis['scores']['overall']}/100.",
                "key_insights": [
                    f"Performance score: {analysis['scores']['performance']}/100",
                    f"Stability score: {analysis['scores']['stability']}/100",
                    f"Critical bugs: {analysis['scores'].get('critical_bugs', 0)}"
                ],
                "priority_actions": [
                    "Fix all critical bugs first",
                    "Optimize page load performance",
                    "Resolve JavaScript errors"
                ]
            }


# =============================================================================
# API ENDPOINTS
# =============================================================================

@app.route("/test-website", methods=["POST"])
async def test_website():
    """Main testing endpoint"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "Invalid JSON body"}), 400

        url = data.get("url")
        if not url:
            return jsonify({"error": "Missing 'url' parameter"}), 400

        # Add https if not present
        if not url.startswith("http"):
            url = "https://" + url

        # Run Playwright test
        tester = PlaywrightTester()
        test_results = await tester.execute(url)

        if not test_results.get("success"):
            return jsonify(test_results), 500

        # Analyze for bugs
        detector = GenuineBugDetector()
        analysis = detector.analyze(test_results)

        # Generate AI summary
        analyst = AIAnalyst()
        ai_summary = await analyst.summarize(analysis, test_results)

        # Build final report
        report = {
            "status": "success",
            "url": test_results["url"],
            "title": test_results["title"],
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "test_duration": test_results["load_time"],
            "health_scores": analysis["scores"],
            "bugs_found": analysis["bugs"],
            "findings": analysis["findings"],
            "recommendations": analysis["recommendations"],
            "ai_summary": ai_summary,
            "screenshots": test_results["screenshots"],
            "metrics": analysis["metrics"]
        }

        logger.info(f"✅ Report generated successfully for {url}")
        return jsonify(report), 200

    except Exception as e:
        logger.error(f"Endpoint error: {str(e)}")
        return jsonify({
            "status": "error",
            "error": str(e),
            "traceback": traceback.format_exc()
        }), 500


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint"""
    return jsonify({
        "status": "active",
        "version": "6.0",
        "service": "AI Playwright Testing Service",
        "model": GEMINI_MODEL,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "features": [
            "Genuine bug detection",
            "Performance analysis",
            "SEO checks",
            "Accessibility testing",
            "Security scanning",
            "AI-powered insights"
        ]
    }), 200


@app.route("/", methods=["GET"])
def index():
    """API documentation"""
    return jsonify({
        "service": "AI Playwright Testing Service v6.0",
        "description": "Comprehensive website testing with genuine bug detection",
        "endpoints": {
            "/test-website": {
                "method": "POST",
                "description": "Test a website for bugs, performance, SEO, and accessibility",
                "body": {
                    "url": "https://example.com"
                },
                "response": {
                    "status": "success",
                    "health_scores": "Object with performance, stability, overall scores",
                    "bugs_found": "Array of detected bugs",
                    "findings": "Categorized findings (critical, high, medium, low)",
                    "recommendations": "Actionable recommendations",
                    "ai_summary": "AI-generated executive summary"
                }
            },
            "/health": {
                "method": "GET",
                "description": "Check service health and status"
            }
        },
        "example": {
            "curl": 'curl -X POST http://localhost:5000/test-website -H "Content-Type: application/json" -d \'{"url": "https://example.com"}\''
        }
    }), 200


# =============================================================================
# MAIN ENTRY POINT
# =============================================================================

if __name__ == "__main__":
    print("""
    ================================================================
    🚀 AI Playwright Testing Service v6.0 - Genuine Bug Detection
    ================================================================
    
    📋 Features:
       ✅ Real bug detection with evidence
       ✅ Performance metrics & scoring
       ✅ SEO analysis
       ✅ Accessibility checks
       ✅ Security scanning
       ✅ AI-powered insights
    
    🌐 Endpoints:
       POST /test-website  - Run comprehensive website test
       GET  /health        - Check service status
       GET  /             - API documentation
    
    📝 Example Request:
       curl -X POST http://localhost:5000/test-website \\
            -H "Content-Type: application/json" \\
            -d '{"url": "https://example.com"}'
    
    ================================================================
    Starting server on http://0.0.0.0:5000
    ================================================================
    """)

    app.run(host="0.0.0.0", port=5000, debug=True)