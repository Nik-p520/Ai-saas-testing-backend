"""
AI Playwright Testing Service - USER-FRIENDLY BUGS & RECOMMENDATIONS
- AI-powered bug detection with clear explanations
- Simple, business-friendly language (no technical jargon)
- Context-aware bug categorization
- Steps to reproduce and fix suggestions
"""

import os, json, asyncio, tempfile, subprocess, uuid, base64, shutil, re
from typing import Dict, Optional, List
from datetime import datetime, timezone
from flask import Flask, request, jsonify
from playwright.async_api import async_playwright
import google.generativeai as genai
from pathlib import Path

app = Flask(__name__)

# -----------------------------------------------------------
# Configuration
# -----------------------------------------------------------
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "your-gemini-api-key-here")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash-exp")
genai.configure(api_key=GEMINI_API_KEY)


# -----------------------------------------------------------
# Playwright Test Generator
# -----------------------------------------------------------
class PlaywrightTestGenerator:
    def __init__(self):
        self.model = genai.GenerativeModel(model_name=GEMINI_MODEL)
        self.system_prompt = """You are a QA automation engineer. 
Generate a Playwright test suite using @playwright/test for the given URL.
Include meaningful assertions, not just navigation.
Use clear, readable names for each test.
Return only raw JavaScript code (no markdown)."""

    async def analyze_page_structure(self, url: str) -> Dict:
        """Enhanced analysis - captures actual page elements"""
        async with async_playwright() as p:
            logs = []
            browser = await p.chromium.launch(headless=True)
            context = await browser.new_context(ignore_https_errors=True)
            page = await context.new_page()

            page_info = {
                "title": "",
                "url": "",
                "headings": [],
                "buttons": [],
                "links": [],
                "forms": [],
                "inputs": [],
                "meta_description": "",
                "has_navigation": False,
                "has_footer": False
            }

            try:
                logs.append(f"🌐 Navigating to {url}")
                await page.goto(url, wait_until="networkidle", timeout=60000)

                page_info["title"] = await page.title()
                page_info["url"] = page.url
                logs.append(f"✅ Page loaded: {page_info['title']}")

                try:
                    # Headings
                    headings_elements = await page.locator("h1, h2, h3").all()
                    for heading in headings_elements[:10]:
                        text = await heading.text_content()
                        if text and text.strip():
                            page_info["headings"].append(text.strip())

                    # Buttons
                    button_elements = await page.locator("button, [role='button'], input[type='submit'], a.btn").all()
                    for button in button_elements[:15]:
                        text = await button.text_content()
                        if not text or not text.strip():
                            text = await button.get_attribute("aria-label") or await button.get_attribute("value") or ""
                        if text and text.strip():
                            page_info["buttons"].append(text.strip())

                    # Links
                    link_elements = await page.locator("a").all()
                    for link in link_elements[:20]:
                        text = await link.text_content()
                        if text and text.strip() and len(text.strip()) > 1:
                            page_info["links"].append(text.strip())

                    # Forms
                    forms = await page.locator("form").count()
                    page_info["forms"] = forms

                    # Input fields
                    inputs = await page.locator("input, textarea, select").all()
                    input_details = []
                    for inp in inputs[:10]:
                        inp_type = await inp.get_attribute("type") or "text"
                        inp_name = await inp.get_attribute("name") or ""
                        inp_placeholder = await inp.get_attribute("placeholder") or ""
                        inp_label = await inp.get_attribute("aria-label") or ""
                        input_details.append({
                            "type": inp_type,
                            "name": inp_name,
                            "placeholder": inp_placeholder,
                            "label": inp_label
                        })
                    page_info["inputs"] = input_details

                    # Meta description
                    meta_elem = page.locator("meta[name='description']")
                    if await meta_elem.count() > 0:
                        meta = await meta_elem.get_attribute("content")
                        page_info["meta_description"] = meta or ""

                    # Navigation and footer
                    page_info["has_navigation"] = await page.locator("nav, [role='navigation']").count() > 0
                    page_info["has_footer"] = await page.locator("footer, [role='contentinfo']").count() > 0

                    logs.append(f"📊 Found: {len(page_info['headings'])} headings, {len(page_info['buttons'])} buttons, {len(page_info['links'])} links")

                except Exception as e:
                    logs.append(f"⚠️ Structure analysis error: {e}")

            except Exception as e:
                logs.append(f"⚠️ Load failed: {e}")
            finally:
                await browser.close()
                logs.append("🚪 Browser closed after analysis.")

            return {"page_info": page_info, "logs": logs}

    async def generate_test_script(self, url: str, test_requirements: Optional[str] = None) -> Dict:
        try:
            analysis = await self.analyze_page_structure(url)
            page_info, logs = analysis["page_info"], analysis["logs"]

            context = f"""
URL: {url}
Title: {page_info.get('title')}
Headings: {', '.join(page_info.get('headings', [])[:5])}
Buttons: {', '.join(page_info.get('buttons', [])[:5])}
Key Links: {', '.join(page_info.get('links', [])[:5])}
Forms: {page_info.get('forms', 0)}
Inputs: {len(page_info.get('inputs', []))}
"""

            prompt = f"""{self.system_prompt}

{context}

Requirements: {test_requirements or "Generate UI interaction tests with validation for the elements found above."}

Output valid Playwright test code only."""

            response = await asyncio.to_thread(self.model.generate_content, prompt)
            script = response.text.strip()

            logs.append("🧠 AI generated Playwright test script.")
            return {
                "success": True,
                "url": url,
                "page_info": page_info,
                "generated_at": datetime.now(timezone.utc).isoformat(),
                "test_script": script,
                "model": GEMINI_MODEL,
                "logs": logs
            }
        except Exception as e:
            return {"success": False, "error": str(e), "url": url}


# -----------------------------------------------------------
# Playwright Test Executor
# -----------------------------------------------------------
class PlaywrightTestExecutor:
    @staticmethod
    def _clean_script(script: str) -> str:
        if script.startswith("```"):
            lines = [l for l in script.splitlines() if not l.strip().startswith("```")]
            return "\n".join(lines)
        return script.strip()

    @staticmethod
    def _format_duration(ms: int) -> str:
        sec = ms / 1000
        return f"{sec:.1f}s" if sec < 60 else f"{int(sec//60)}m {int(sec%60)}s"

    @staticmethod
    def _extract_json(stdout: str) -> Optional[dict]:
        if not stdout:
            return None
        try:
            return json.loads(stdout)
        except Exception:
            start = stdout.find('{"suites"')
            if start == -1:
                return None
            for end in range(len(stdout), start, -1):
                try:
                    return json.loads(stdout[start:end])
                except Exception:
                    continue
            return None

    @staticmethod
    def _parse_test_script(script: str) -> Dict:
        parsed = {
            "test_names": [],
            "selectors": [],
            "assertions": [],
            "interactions": [],
            "element_texts": []
        }

        test_matches = re.findall(r"test\(['\"](.+?)['\"]\s*,", script)
        parsed["test_names"] = test_matches[:10]

        selector_patterns = [
            (r"getByRole\(['\"](\w+)['\"](?:,\s*\{\s*name:\s*['\"](.+?)['\"]\})?", "role"),
            (r"getByText\(['\"](.+?)['\"]\)", "text"),
            (r"getByLabel\(['\"](.+?)['\"]\)", "label"),
            (r"getByPlaceholder\(['\"](.+?)['\"]\)", "placeholder"),
            (r"getByTitle\(['\"](.+?)['\"]\)", "title"),
            (r"locator\(['\"](.+?)['\"]\)", "css")
        ]

        for pattern, selector_type in selector_patterns:
            matches = re.findall(pattern, script)
            for match in matches:
                if isinstance(match, tuple):
                    text = " ".join(filter(None, match))
                    parsed["selectors"].append(text)
                    if match[-1]:
                        parsed["element_texts"].append(match[-1])
                else:
                    parsed["selectors"].append(match)
                    if selector_type in ["text", "label", "placeholder", "title"]:
                        parsed["element_texts"].append(match)

        return parsed

    @staticmethod
    def _categorize_bug_severity(error: str, test_name: str) -> str:
        """Intelligently categorize bug severity based on error type"""
        error_lower = error.lower()
        test_lower = test_name.lower()

        # Critical - page won't load, timeouts, crashes
        critical_keywords = ['timeout', 'crash', 'cannot load', 'failed to load', 'navigation', 'net::err']
        if any(keyword in error_lower for keyword in critical_keywords):
            return "critical"

        # High - core functionality broken
        high_keywords = ['login', 'checkout', 'payment', 'submit', 'form', 'authentication', 'authorization']
        if any(keyword in test_lower for keyword in high_keywords):
            return "high"

        # Medium - UI issues, missing elements
        medium_keywords = ['not found', 'not visible', 'missing', 'locator']
        if any(keyword in error_lower for keyword in medium_keywords):
            return "medium"

        # Default to high for unknown failures
        return "high"

    @staticmethod
    def _generate_user_friendly_bugs(
            failures: List[Dict],
            test_url: str,
            page_info: Optional[Dict],
            script_analysis: Dict
    ) -> List[Dict]:
        """AI-powered user-friendly bug generation"""
        if not failures:
            return []

        try:
            model = genai.GenerativeModel(model_name=GEMINI_MODEL)

            page_title = page_info.get('title', 'Unknown') if page_info else 'Unknown'

            # Build context
            context_parts = [
                f"WEBSITE: {test_url} ({page_title})",
                "",
                "FAILED TESTS:"
            ]

            for i, failure in enumerate(failures[:5], 1):
                context_parts.append(f"{i}. Test: '{failure.get('title', 'Unknown')}'")
                context_parts.append(f"   Error: {failure.get('error', 'No details')[:300]}")
                context_parts.append("")

            # Add page elements if available
            if page_info:
                context_parts.append("PAGE ELEMENTS:")

                if page_info.get("buttons"):
                    buttons = ", ".join([f"'{b}'" for b in page_info['buttons'][:5]])
                    context_parts.append(f"  Buttons: {buttons}")

                if page_info.get("links"):
                    links = ", ".join([f"'{l}'" for l in page_info['links'][:5]])
                    context_parts.append(f"  Links: {links}")

                context_parts.append("")

            # Add what was tested
            if script_analysis.get("element_texts"):
                context_parts.append("WHAT WAS BEING TESTED:")
                for elem in script_analysis["element_texts"][:5]:
                    context_parts.append(f"  - '{elem}'")
                context_parts.append("")

            context_text = "\n".join(context_parts)

            prompt = f"""You are explaining website bugs to NON-TECHNICAL business users (product managers, business owners).

{context_text}

For each failed test, create a user-friendly bug report.

🎯 CRITICAL RULES:

1. **NO TECHNICAL JARGON** - Don't use: "locator", "selector", "DOM", "timeout", "assertion", "element"
2. **SIMPLE LANGUAGE** - Explain like talking to someone who doesn't code
3. **USER IMPACT** - Explain what this means for actual website visitors
4. **BE SPECIFIC** - Mention actual button/link names from the page
5. **ACTIONABLE** - Suggest what needs to be fixed (not how to code it)

✅ GOOD EXAMPLES:
Title: "The 'Sign Up' button can't be found"
Description: "When users try to sign up, the test couldn't find the 'Sign Up' button on the page. This might mean: (1) The button text changed from 'Sign Up' to something else, (2) The button was removed, or (3) It's hidden. Impact: New users can't create accounts. Fix: Check if the button still exists and update the test if the button text changed."

Title: "Contact form submission takes too long"
Description: "When testing the contact form, it took longer than 30 seconds to submit, which caused the test to fail. This suggests the form might be very slow for real users. Impact: Users may give up waiting and leave your site. Fix: Investigate why the form is slow - check server response time or optimize the submission process."

❌ BAD EXAMPLES:
Title: "Locator timeout at line 45"
Description: "getByRole('button', {{name: 'Sign Up'}}) timed out after 30000ms. Selector needs updating."

📋 FORMAT YOUR RESPONSE AS JSON:

Return an array of bugs, one for each failure. For each bug:

[
  {{
    "bugId": "bug_unique_id",
    "title": "Clear, simple title explaining what's broken (mention specific button/feature name)",
    "description": "1-2 sentence explanation of the problem in plain English. Then explain what this means for users. Then suggest a simple fix. Maximum 400 characters.",
    "severity": "critical|high|medium|low",
    "steps_to_reproduce": ["Step 1: Go to [page]", "Step 2: Click [button]", "Step 3: Observe [issue]"],
    "expected_result": "What should happen (in user terms)",
    "actual_result": "What actually happened (in user terms)",
    "user_impact": "How this affects real website visitors"
  }}
]

SEVERITY GUIDE:
- critical: Page won't load, site crashes, nothing works
- high: Core feature broken (login, checkout, forms don't work)
- medium: Feature works but has issues (button hard to find, slow)
- low: Minor cosmetic issues

Return ONLY the JSON array, no markdown."""

            print("🐛 Generating user-friendly bug reports...")
            response = model.generate_content(prompt)
            text = response.text.strip()

            # Clean markdown
            if text.startswith("```"):
                text = "\n".join([
                    line for line in text.splitlines()
                    if not line.strip().startswith("```") and line.strip() != "json"
                ])

            # Parse bugs
            try:
                bugs = json.loads(text)
                if not isinstance(bugs, list):
                    bugs = [bugs]

                validated_bugs = []
                for i, bug in enumerate(bugs[:5]):  # Max 5 bugs
                    # Ensure we don't go out of bounds
                    if i >= len(failures):
                        break

                    # Validate structure
                    validated_bug = {
                        "bugId": bug.get("bugId", f"bug_{uuid.uuid4().hex[:12]}"),
                        "title": str(bug.get("title", failures[i].get("title", "Test Failure")))[:255],
                        "description": str(bug.get("description", "A test failed"))[:800],
                        "severity": bug.get("severity", "high").lower(),
                        "steps_to_reproduce": bug.get("steps_to_reproduce", [
                            f"1. Visit {test_url}",
                            f"2. Run automated test: {failures[i].get('title', 'Unknown')}",
                            "3. Test fails"
                        ]),
                        "expected_result": bug.get("expected_result", "Test should pass"),
                        "actual_result": bug.get("actual_result", "Test failed"),
                        "user_impact": bug.get("user_impact", "Users may experience issues with this feature")
                    }

                    # Validate severity
                    valid_severities = ["critical", "high", "medium", "low"]
                    if validated_bug["severity"] not in valid_severities:
                        validated_bug["severity"] = PlaywrightTestExecutor._categorize_bug_severity(
                            failures[i].get("error", ""),
                            failures[i].get("title", "")
                        )

                    validated_bugs.append(validated_bug)
                    print(f"  ✓ {validated_bug['title'][:70]} [{validated_bug['severity']}]")

                print(f"✅ Generated {len(validated_bugs)} user-friendly bug reports")
                return validated_bugs

            except json.JSONDecodeError as e:
                print(f"❌ JSON parse error in bug generation: {e}")
                print(f"Raw response: {text[:500]}")
                return PlaywrightTestExecutor._create_simple_bugs(failures, test_url, page_info)

        except Exception as e:
            print(f"❌ Error generating AI bugs: {e}")
            return PlaywrightTestExecutor._create_simple_bugs(failures, test_url, page_info)

    @staticmethod
    def _create_simple_bugs(failures: List[Dict], test_url: str, page_info: Optional[Dict]) -> List[Dict]:
        """Fallback: Create simple user-friendly bugs without AI"""
        bugs = []
        page_title = page_info.get('title', 'the website') if page_info else 'the website'

        for failure in failures[:5]:
            test_name = failure.get('title', 'A test')
            error = failure.get('error', 'An unknown error occurred')

            # Categorize severity
            severity = PlaywrightTestExecutor._categorize_bug_severity(error, test_name)

            # Extract element name if possible
            element_match = re.search(r"'([^']+)'", test_name)
            element_name = element_match.group(1) if element_match else "a page element"

            # Create user-friendly description
            if "timeout" in error.lower():
                title = f"'{test_name}' is taking too long to respond"
                description = f"When testing '{test_name}' on {page_title}, the page took longer than expected (over 30 seconds). This might mean the feature is very slow for real users. Check if there are performance issues or if the page is loading correctly."
                user_impact = "Users may experience slow loading times or give up waiting"
                actual_result = "Page or element took too long to load (timeout)"
            elif "not found" in error.lower() or "locator" in error.lower():
                title = f"Can't find '{element_name}' on {page_title}"
                description = f"The automated test couldn't find '{element_name}' on the page. This could mean: (1) The element was removed or renamed, (2) It's hidden from view, or (3) The page structure changed. Users might not be able to access this feature."
                user_impact = "Users may not be able to use this feature if it's missing or hard to find"
                actual_result = f"'{element_name}' was not found on the page"
            else:
                title = f"Issue with '{test_name}' on {page_title}"
                description = f"The test '{test_name}' failed on {test_url}. Error: {error[:150]}. This needs investigation to ensure the feature works correctly for users."
                user_impact = "Users may encounter errors when using this feature"
                actual_result = "Test failed with an error"

            bugs.append({
                "bugId": f"bug_{uuid.uuid4().hex[:12]}",
                "title": title[:255],
                "description": description[:800],
                "severity": severity,
                "steps_to_reproduce": [
                    f"1. Visit {test_url}",
                    f"2. Try to use the '{element_name}' feature",
                    f"3. Observe the issue"
                ],
                "expected_result": f"The '{element_name}' should work correctly",
                "actual_result": actual_result,
                "user_impact": user_impact
            })

        return bugs

    @staticmethod
    def _validate_recommendation(rec: Dict) -> Dict:
        if "recommendationId" not in rec or not rec["recommendationId"]:
            rec["recommendationId"] = f"rec_{uuid.uuid4().hex[:12]}"

        if "title" not in rec or not rec["title"]:
            rec["title"] = "Test Recommendation"
        rec["title"] = str(rec["title"])[:255]

        if "description" not in rec or not rec["description"]:
            rec["description"] = "No description provided"
        rec["description"] = str(rec["description"])

        valid_impacts = ["low", "medium", "high"]
        if "impact" not in rec or rec["impact"].lower() not in valid_impacts:
            rec["impact"] = "medium"
        rec["impact"] = rec["impact"].lower()

        valid_categories = ["performance", "security", "accessibility", "seo", "ux"]
        if "category" not in rec or rec["category"].lower() not in valid_categories:
            rec["category"] = "ux"
        rec["category"] = rec["category"].lower()

        return rec

    @staticmethod
    def _ask_gemini_for_recommendations(
            failures: List[Dict],
            test_url: str = "",
            test_results: Optional[Dict] = None,
            script_content: str = "",
            page_info: Optional[Dict] = None,
            screenshot_path: Optional[str] = None
    ) -> List[Dict]:
        """Generate user-friendly, non-technical recommendations"""
        try:
            model = genai.GenerativeModel(model_name=GEMINI_MODEL)
            script_analysis = PlaywrightTestExecutor._parse_test_script(script_content)

            # Build context
            context_parts = [
                f"🌐 WEBSITE: {test_url}",
                f"📄 PAGE: {page_info.get('title', 'Unknown') if page_info else 'Unknown'}",
                ""
            ]

            if page_info:
                context_parts.append("📊 PAGE ELEMENTS:")

                if page_info.get("headings"):
                    headings = ", ".join([f"'{h}'" for h in page_info['headings'][:5]])
                    context_parts.append(f"  Headings: {headings}")

                if page_info.get("buttons"):
                    buttons = ", ".join([f"'{b}'" for b in page_info['buttons'][:5]])
                    context_parts.append(f"  Buttons: {buttons}")

                if page_info.get("links"):
                    links = ", ".join([f"'{l}'" for l in page_info['links'][:5]])
                    context_parts.append(f"  Links: {links}")

                context_parts.append("")

            if test_results:
                total_tests = sum(
                    len(spec.get("tests", []))
                    for suite in test_results.get("suites", [])
                    for spec in suite.get("specs", [])
                )
                passed_tests = sum(
                    1 for suite in test_results.get("suites", [])
                    for spec in suite.get("specs", [])
                    for test in spec.get("tests", [])
                    for result in test.get("results", [])
                    if result.get("status") == "passed"
                )
                failed_tests = total_tests - passed_tests
                context_parts.append(f"🧪 TESTS: {passed_tests} passed, {failed_tests} failed")
                context_parts.append("")

            if script_analysis["test_names"]:
                context_parts.append("📝 WHAT WAS TESTED:")
                for name in script_analysis["test_names"][:5]:
                    context_parts.append(f"  - {name}")
                context_parts.append("")

            if script_analysis["element_texts"]:
                context_parts.append("🎯 ELEMENTS CHECKED:")
                for text in script_analysis["element_texts"][:6]:
                    context_parts.append(f"  - '{text}'")
                context_parts.append("")

            if failures:
                context_parts.append("❌ ISSUES FOUND:")
                for i, f in enumerate(failures[:3], 1):
                    context_parts.append(f"  {i}. {f.get('title', 'Unknown')}")
                    context_parts.append(f"     Problem: {f.get('error', 'No details')[:150]}")
                context_parts.append("")

            context_text = "\n".join(context_parts)

            # User-friendly prompt
            if not failures:
                prompt = f"""You are a website quality advisor speaking to NON-TECHNICAL business users.

{context_text}

All automated tests PASSED ✅. Provide 2-3 actionable recommendations to improve this website.

🎯 CRITICAL WRITING RULES:

1. **USE SIMPLE LANGUAGE** - Pretend you're talking to your mom or a friend who isn't a developer
2. **NO TECHNICAL JARGON** - Don't use: "selector", "ARIA", "DOM", "attribute", "locator", "assertion", "viewport", "getByRole"
3. **BE SPECIFIC** - Mention the actual button/link text from the page
4. **EXPLAIN WHY IT MATTERS** - Focus on business impact
5. **ACTIONABLE** - Tell them WHAT to do, not HOW to code it

✅ GOOD EXAMPLES (simple language):
- "Add a descriptive label to the 'Sign Up' button so screen readers can announce it to blind users. This makes your site accessible to 15% more potential customers."
- "Test if the 'Contact Us' form works on mobile phones. Over 60% of your visitors use phones, and form issues can lose customers."

Format as JSON array with recommendationId, title, description, impact, category.

Return ONLY the JSON array."""
            else:
                prompt = f"""You are a website quality advisor explaining problems to NON-TECHNICAL business users.

{context_text}

Some automated tests FAILED ❌. Provide 2-3 recommendations to prevent these issues.

Use simple language, focus on business impact, be specific about which elements failed.

Format as JSON array with recommendationId, title, description, impact, category.

Return ONLY the JSON array."""

            print("🤖 Generating user-friendly recommendations...")
            response = model.generate_content(prompt)
            text = response.text.strip()

            if text.startswith("```"):
                text = "\n".join([
                    line for line in text.splitlines()
                    if not line.strip().startswith("```") and line.strip() != "json"
                ])

            try:
                recommendations = json.loads(text)
                if not isinstance(recommendations, list):
                    recommendations = [recommendations]

                validated = []
                for rec in recommendations[:5]:
                    validated_rec = PlaywrightTestExecutor._validate_recommendation(rec)

                    desc_lower = validated_rec["description"].lower()
                    title_lower = validated_rec["title"].lower()

                    has_url_ref = test_url.lower() in desc_lower or test_url.lower() in title_lower

                    has_element_ref = False
                    if page_info:
                        all_elements = (
                                page_info.get("headings", [])[:5] +
                                page_info.get("buttons", [])[:5] +
                                script_analysis.get("test_names", [])[:5]
                        )
                        has_element_ref = any(
                            elem.lower() in desc_lower or elem.lower() in title_lower
                            for elem in all_elements if elem and len(elem) > 3
                        )

                    if failures or has_url_ref or has_element_ref:
                        validated.append(validated_rec)
                        print(f"✅ Accepted: {validated_rec['title'][:80]}")
                    else:
                        print(f"❌ Rejected (too generic): {validated_rec['title'][:80]}")

                if not validated:
                    print("⚠️ All AI recommendations rejected, creating fallback...")
                    validated = [PlaywrightTestExecutor._create_user_friendly_fallback(
                        failures, test_url, page_info, script_analysis
                    )]

                print(f"✅ Generated {len(validated)} user-friendly recommendations")
                return validated

            except json.JSONDecodeError as e:
                print(f"❌ JSON parse error: {e}")
                return [PlaywrightTestExecutor._create_user_friendly_fallback(
                    failures, test_url, page_info, script_analysis
                )]

        except Exception as e:
            print(f"❌ Gemini API error: {e}")
            return [PlaywrightTestExecutor._create_user_friendly_fallback(
                failures, test_url, page_info, script_analysis
            )]

    @staticmethod
    def _create_user_friendly_fallback(
            failures: List[Dict],
            test_url: str,
            page_info: Optional[Dict],
            script_analysis: Dict
    ) -> Dict:
        """Creates user-friendly fallback recommendation"""

        page_title = page_info.get('title', '') if page_info else ''
        page_name = page_title if page_title else test_url

        if failures:
            first_failure = failures[0]
            test_name = first_failure.get('title', 'A test')
            error = first_failure.get('error', 'An unexpected error occurred')[:150]

            return {
                "recommendationId": f"rec_{uuid.uuid4().hex[:12]}",
                "title": f"Fix an issue with '{test_name}' on your website",
                "description": f"One of our automated tests ('{test_name}') found an issue on {page_name}. The problem: {error}. We recommend having a developer review this to ensure all features work properly for your visitors.",
                "impact": "high",
                "category": "ux"
            }

        actual_elements = []
        untested_elements = []

        if page_info:
            headings = page_info.get("headings", [])[:3]
            buttons = page_info.get("buttons", [])[:3]
            element_texts = script_analysis.get("element_texts", [])

            for tested_text in element_texts[:5]:
                tested_lower = tested_text.lower()
                for heading in headings:
                    if heading.lower() in tested_lower or tested_lower in heading.lower():
                        actual_elements.append(f"the '{heading}' heading")
                        break
                for button in buttons:
                    if button.lower() in tested_lower or tested_lower in button.lower():
                        actual_elements.append(f"the '{button}' button")
                        break

            if not actual_elements:
                actual_elements = [f"the '{h}' heading" for h in headings if h]
                actual_elements += [f"the '{b}' button" for b in buttons if b]
                actual_elements = actual_elements[:3]

            all_buttons = page_info.get("buttons", [])
            tested_texts_lower = [elem.lower() for elem in element_texts]

            for button in all_buttons[:10]:
                if button and button.lower() not in " ".join(tested_texts_lower):
                    untested_elements.append(f"'{button}'")

        if untested_elements:
            untested_list = ", ".join(untested_elements[:3])
            elements_tested = ", ".join(actual_elements[:2]) if actual_elements else "several elements"

            return {
                "recommendationId": f"rec_{uuid.uuid4().hex[:12]}",
                "title": f"Test additional buttons on {page_name}",
                "description": f"Good news! All our tests passed on {test_url}. We checked {elements_tested} and they work great. However, we noticed these buttons haven't been tested yet: {untested_list}. Adding tests for these will ensure all clickable elements work correctly for your users.",
                "impact": "medium",
                "category": "ux"
            }

        if actual_elements:
            first_element = actual_elements[0]
            elements_list = ", ".join(actual_elements[:3]) if len(actual_elements) <= 3 else f"{actual_elements[0]}, {actual_elements[1]}, and others"

            return {
                "recommendationId": f"rec_{uuid.uuid4().hex[:12]}",
                "title": f"Expand testing for {page_name}",
                "description": f"All tests passed on {test_url}! We successfully verified {elements_list}. To make your website even more reliable, consider testing these scenarios: (1) What happens when someone tries to use {first_element} with invalid information, (2) How it looks and works on mobile phones, (3) Whether keyboard users can navigate easily. This helps ensure a great experience for all your visitors.",
                "impact": "low",
                "category": "accessibility"
            }

        return {
            "recommendationId": f"rec_{uuid.uuid4().hex[:12]}",
            "title": f"Continue improving {page_name}",
            "description": f"Your website ({page_name}) passed our initial automated tests. To maintain high quality, we recommend regularly testing key features like forms, buttons, and user workflows. This helps catch issues before your customers do and ensures a smooth experience for everyone visiting {test_url}.",
            "impact": "medium",
            "category": "ux"
        }

    @staticmethod
    def run_script(script_content: str, test_url: str = "", page_info: Optional[Dict] = None) -> Dict:
        """Execute Playwright test script and generate user-friendly bugs & recommendations"""
        script = PlaywrightTestExecutor._clean_script(script_content)
        tmp_file = os.path.join(tempfile.gettempdir(), f"ai_test_{uuid.uuid4().hex[:6]}.spec.js")
        screenshot_dir = tempfile.mkdtemp(prefix="pw_screens_")

        logs, failures = [], []
        screenshots, duration = [], "0s"
        first_screenshot = None
        logs.append(f"📁 Screenshot dir: {screenshot_dir}")

        screenshot_hook = f"""
// --- Auto Screenshot Hook ---
import {{ test }} from '@playwright/test';
import path from 'path';
test.afterEach(async ({{ page }}, testInfo) => {{
  try {{
    const dir = process.env.PW_SCREENSHOT_DIR || "{screenshot_dir.replace(os.sep, '/')}";
    const safe = testInfo.title.replace(/[^a-zA-Z0-9-_]/g, '_').slice(0,100);
    const file = path.join(dir, `${{safe}}_${{Date.now()}}.png`);
    await page.screenshot({{ path: file, fullPage: true }});
  }} catch(e) {{
    console.warn('Screenshot hook error', e);
  }}
}});
"""

        script_final = screenshot_hook + "\n" + script

        try:
            with open(tmp_file, "w", encoding="utf-8") as f:
                f.write(script_final)
            logs.append(f"📝 Saved test file: {tmp_file}")

            start = datetime.now()
            env = os.environ.copy()
            env["PW_SCREENSHOT_DIR"] = screenshot_dir
            cmd = f'npx playwright test "{tmp_file}" --reporter=json --workers=1'
            logs.append(f"⚙️ Executing: {cmd}")

            proc = subprocess.run(cmd, capture_output=True, text=True, shell=True, env=env, timeout=300)
            duration = PlaywrightTestExecutor._format_duration(
                int((datetime.now() - start).total_seconds() * 1000)
            )

            if proc.stderr:
                stderr_lines = proc.stderr.splitlines()[:6]
                if stderr_lines:
                    logs.append("⚠️ STDERR:")
                    logs.extend(stderr_lines)

            result = PlaywrightTestExecutor._extract_json(proc.stdout)
            if result:
                for s in result.get("suites", []):
                    for spec in s.get("specs", []):
                        for test in spec.get("tests", []):
                            for r in test.get("results", []):
                                title = test.get("title", "Unnamed")
                                status = r.get("status", "unknown")
                                logs.append(f"▶ {title} — {status}")

                                if status in ["failed", "timedOut"]:
                                    error_obj = r.get("error", {})
                                    msg = error_obj.get("message", "Unknown error") if isinstance(error_obj, dict) else str(error_obj)
                                    failures.append({"title": title, "error": msg})
            else:
                logs.append("❌ Could not parse Playwright JSON output.")
                logs.append(proc.stdout[:800])

            # Collect screenshots
            try:
                screenshot_files = sorted(
                    [f for f in os.listdir(screenshot_dir) if f.endswith('.png')],
                    key=lambda x: os.path.getmtime(os.path.join(screenshot_dir, x))
                )

                for fname in screenshot_files:
                    fpath = os.path.join(screenshot_dir, fname)
                    if not first_screenshot:
                        first_screenshot = fpath

                    with open(fpath, "rb") as img:
                        b64 = base64.b64encode(img.read()).decode("ascii")
                        screenshots.append({"filename": fname, "b64": b64})

                logs.append(f"📸 Collected {len(screenshots)} screenshots.")
            except Exception as e:
                logs.append(f"⚠️ Screenshot collection error: {e}")

            status = "failed" if failures else "passed"

            print(f"\n{'=' * 60}")
            print(f"🤖 Generating user-friendly bugs & recommendations for: {test_url}")
            print(f"   Status: {status}")
            print(f"   Failures: {len(failures)}")
            print(f"   Page info available: {bool(page_info)}")
            print(f"{'=' * 60}\n")

            # Generate AI-powered bugs
            script_analysis = PlaywrightTestExecutor._parse_test_script(script)
            bugs = PlaywrightTestExecutor._generate_user_friendly_bugs(
                failures=failures,
                test_url=test_url,
                page_info=page_info,
                script_analysis=script_analysis
            )

            # Generate recommendations
            recommendations = PlaywrightTestExecutor._ask_gemini_for_recommendations(
                failures=failures,
                test_url=test_url,
                test_results=result,
                script_content=script,
                page_info=page_info,
                screenshot_path=first_screenshot
            )

            logs.append(f"✅ Generated {len(bugs)} bug reports and {len(recommendations)} recommendations.")
            logs.append("🏁 Test execution complete.")

            return {
                "success": True,
                "status": status,
                "logs": list(dict.fromkeys(logs)),
                "bugs": bugs,
                "recommendations": recommendations,
                "duration": duration,
                "browser": "chromium",
                "screenshots": screenshots,
                "results": result
            }

        except subprocess.TimeoutExpired:
            logs.append("❌ Test execution timeout (300s limit)")
            timeout_failure = [{"title": "Test Timeout", "error": "Execution exceeded 300s limit"}]

            bugs = [{
                "bugId": f"bug_{uuid.uuid4().hex[:12]}",
                "title": "Website is taking too long to respond",
                "description": "The automated tests couldn't complete because your website took longer than 5 minutes to respond. This usually means: (1) The server is very slow, (2) The page has heavy content that takes forever to load, or (3) There's a technical issue preventing the page from loading. Impact: Users will likely experience very slow loading times or the page may not load at all. Fix: Check server performance and page load speed.",
                "severity": "critical",
                "steps_to_reproduce": [
                    f"1. Visit {test_url}",
                    "2. Wait for page to load",
                    "3. Page takes over 5 minutes or doesn't load"
                ],
                "expected_result": "Page should load in under 3-5 seconds",
                "actual_result": "Page took over 5 minutes (test timeout)",
                "user_impact": "Users cannot access your website due to extreme slowness"
            }]

            script_analysis = PlaywrightTestExecutor._parse_test_script(script)
            recs = PlaywrightTestExecutor._ask_gemini_for_recommendations(
                failures=timeout_failure,
                test_url=test_url,
                test_results=None,
                script_content=script,
                page_info=page_info
            )
            return {
                "success": False,
                "status": "timeout",
                "logs": logs,
                "bugs": bugs,
                "recommendations": recs,
                "duration": duration,
                "screenshots": []
            }
        except Exception as e:
            logs.append(f"❌ Unexpected error: {e}")
            error_failure = [{"title": "Execution Error", "error": str(e)}]

            bugs = [{
                "bugId": f"bug_{uuid.uuid4().hex[:12]}",
                "title": "Automated testing encountered a technical issue",
                "description": f"The automated testing system ran into an unexpected problem: {str(e)[:200]}. This might be a temporary issue with the testing setup rather than your website. Try running the test again. If the problem persists, contact support.",
                "severity": "medium",
                "steps_to_reproduce": [
                    "1. Run automated test",
                    "2. System encounters error",
                    "3. Test cannot complete"
                ],
                "expected_result": "Tests should run successfully",
                "actual_result": "Testing system error",
                "user_impact": "Unable to verify website quality automatically"
            }]

            script_analysis = PlaywrightTestExecutor._parse_test_script(script)
            recs = PlaywrightTestExecutor._ask_gemini_for_recommendations(
                failures=error_failure,
                test_url=test_url,
                test_results=None,
                script_content=script,
                page_info=page_info
            )
            return {
                "success": False,
                "status": "error",
                "logs": logs,
                "bugs": bugs,
                "recommendations": recs,
                "duration": duration,
                "screenshots": []
            }
        finally:
            try:
                if os.path.exists(tmp_file):
                    os.remove(tmp_file)
                if os.path.exists(screenshot_dir):
                    shutil.rmtree(screenshot_dir)
            except Exception as cleanup_error:
                print(f"⚠️ Cleanup error: {cleanup_error}")


# -----------------------------------------------------------
# Flask Routes
# -----------------------------------------------------------
@app.route("/generate-tests", methods=["POST"])
async def generate_tests():
    data = request.get_json(silent=True) or {}
    url = data.get("url")

    if not url:
        return jsonify({"success": False, "error": "Missing 'url' parameter"}), 400

    gen = PlaywrightTestGenerator()
    result = await gen.generate_test_script(url, data.get("test_requirements"))

    return jsonify(result), (200 if result.get("success") else 500)


@app.route("/execute-tests", methods=["POST"])
def execute_tests():
    data = request.get_json(silent=True) or {}
    script = data.get("test_script")
    url = data.get("url", "")
    page_info = data.get("page_info")

    if not script:
        return jsonify({"success": False, "error": "Missing 'test_script' parameter"}), 400

    executor = PlaywrightTestExecutor()
    result = executor.run_script(script, url, page_info)

    return jsonify(result), (200 if result.get("success") else 500)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "healthy",
        "model": GEMINI_MODEL,
        "features": [
            "AI-powered bug detection",
            "User-friendly bug reports",
            "Simple, non-technical language",
            "Context-aware recommendations",
            "Business-focused insights"
        ]
    })


@app.route("/", methods=["GET"])
def index():
    return jsonify({
        "service": "AI Playwright Testing Service",
        "version": "4.0.0 - User-Friendly Bugs & Recommendations",
        "endpoints": {
            "/generate-tests": {
                "method": "POST",
                "body": {
                    "url": "https://example.com",
                    "test_requirements": "optional"
                },
                "returns": "Generated test script + page structure"
            },
            "/execute-tests": {
                "method": "POST",
                "body": {
                    "url": "https://example.com",
                    "test_script": "playwright test code",
                    "page_info": "page structure from /generate-tests"
                },
                "returns": "Test results + USER-FRIENDLY bugs + AI recommendations"
            },
            "/health": {
                "method": "GET",
                "returns": "Service health status"
            }
        }
    })


if __name__ == "__main__":
    print("\n" + "=" * 70)
    print("🚀 AI Playwright Testing - USER-FRIENDLY BUGS & RECOMMENDATIONS")
    print("=" * 70)
    print(f"📡 Gemini Model: {GEMINI_MODEL}")
    print(f"🌐 Server: http://0.0.0.0:5000")
    print("\n✨ Features:")
    print("   ✓ AI-powered bug detection")
    print("   ✓ Simple, non-technical bug descriptions")
    print("   ✓ Steps to reproduce + user impact analysis")
    print("   ✓ Business-focused recommendations")
    print("   ✓ Clear explanations anyone can understand")
    print("=" * 70 + "\n")

    app.run(host="0.0.0.0", port=5000, debug=True)