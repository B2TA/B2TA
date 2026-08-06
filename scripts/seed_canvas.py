#!/usr/bin/env python3
"""Seed the Canvas course with the essay rubric B2TA is designed around.

The instance ships with Canvas's default stub rubric (one criterion, Full/No
Marks), which gives the colour-coded evidence UI nothing to work with. This
creates the five-criterion rubric mirrored in src/App.tsx CRITERIA and attaches
it to the assignment for grading.

Usage:
    export CANVAS_URL=https://canvas.cic.wtarit.me
    export CANVAS_TOKEN=...            # TA token; needs manage_rubrics
    python3 scripts/seed_canvas.py --course 1 --assignment 1

Idempotency: Canvas has no upsert for rubrics. Re-running creates a *new*
rubric and re-points the association at it, leaving the old one orphaned in the
course. Pass --dry-run first if unsure.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

# Mirrors CRITERIA in src/App.tsx. Keep the two in sync: the UI's colour
# assignment is positional, so reordering here reshuffles highlight colours.
CRITERIA = [
    {
        "description": "Thesis Clarity",
        "long_description": "Central argument is clearly stated, arguable, and appears early in the essay.",
        "points": 5,
        "ratings": [
            (5, "Exemplary", "Thesis is precise, arguable, and elegantly positioned."),
            (4, "Proficient", "Thesis is clear and arguable with minor ambiguity."),
            (3, "Developing", "Thesis present but broad or partially unclear."),
            (2, "Beginning", "Thesis implied but not directly stated."),
            (1, "Insufficient", "No identifiable thesis."),
        ],
    },
    {
        "description": "Use of Evidence",
        "long_description": "Integrates at least 3 cited sources; quotations and paraphrases directly support claims.",
        "points": 5,
        "ratings": [
            (5, "Exemplary", "Evidence is varied, well-integrated, and thoroughly analyzed."),
            (4, "Proficient", "Evidence supports claims; minor integration issues."),
            (3, "Developing", "Evidence present but thin or under-analyzed."),
            (2, "Beginning", "Minimal sources; evidence often dropped in without context."),
            (1, "Insufficient", "Little to no evidence cited."),
        ],
    },
    {
        "description": "Organization",
        "long_description": "Logical paragraph structure with clear transitions linking ideas across sections.",
        "points": 5,
        "ratings": [
            (5, "Exemplary", "Seamless flow; every transition serves the argument."),
            (4, "Proficient", "Well-organized; occasional abrupt transitions."),
            (3, "Developing", "Basic structure present but transitions weak."),
            (2, "Beginning", "Sections feel disjointed; logic hard to follow."),
            (1, "Insufficient", "No discernible organizational logic."),
        ],
    },
    {
        "description": "Grammar & Mechanics",
        "long_description": "Minimal errors in grammar, punctuation, and sentence structure throughout.",
        "points": 3,
        "ratings": [
            (3, "Proficient", "Virtually error-free; prose is polished."),
            (2, "Developing", "A few noticeable errors that do not impede reading."),
            (1, "Beginning", "Frequent errors that impede comprehension."),
        ],
    },
    {
        "description": "Citation Format",
        "long_description": "All in-text citations and bibliography entries follow MLA or APA format consistently.",
        "points": 2,
        "ratings": [
            (2, "Proficient", "Consistent, correct citation format throughout."),
            (1, "Developing", "Minor citation errors or inconsistencies."),
            (0, "Insufficient", "Missing citations or wrong format."),
        ],
    },
]


def request(method, base, token, path, fields=None):
    url = f"{base}/api/v1{path}"
    data = urllib.parse.urlencode(fields, doseq=True).encode() if fields else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    if data:
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read() or "null")
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> {e.code}\n{e.read().decode()[:600]}")


def build_fields(course, assignment, title):
    """Canvas wants criteria as bracket-indexed form params, not JSON."""
    f = {
        "rubric[title]": title,
        "rubric[free_form_criterion_comments]": "0",
        "rubric_association[association_id]": str(assignment),
        "rubric_association[association_type]": "Assignment",
        "rubric_association[purpose]": "grading",
        "rubric_association[use_for_grading]": "1",
        "rubric_association[hide_score_total]": "0",
    }
    for i, c in enumerate(CRITERIA):
        f[f"rubric[criteria][{i}][description]"] = c["description"]
        f[f"rubric[criteria][{i}][long_description]"] = c["long_description"]
        f[f"rubric[criteria][{i}][points]"] = str(c["points"])
        f[f"rubric[criteria][{i}][criterion_use_range]"] = "0"
        for j, (pts, label, desc) in enumerate(c["ratings"]):
            f[f"rubric[criteria][{i}][ratings][{j}][description]"] = label
            f[f"rubric[criteria][{i}][ratings][{j}][long_description]"] = desc
            f[f"rubric[criteria][{i}][ratings][{j}][points]"] = str(pts)
    return f


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--course", type=int, default=1)
    p.add_argument("--assignment", type=int, default=1)
    p.add_argument("--title", default="Essay 3: Argumentative Analysis Rubric")
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()

    base = os.environ.get("CANVAS_URL", "").rstrip("/")
    token = os.environ.get("CANVAS_TOKEN", "")
    if not base or not token:
        sys.exit("Set CANVAS_URL and CANVAS_TOKEN")

    total = sum(c["points"] for c in CRITERIA)
    fields = build_fields(args.course, args.assignment, args.title)

    if args.dry_run:
        print(f"Would create '{args.title}' ({total} pts) on assignment {args.assignment}:")
        for c in CRITERIA:
            print(f"  {c['description']:<22} {c['points']} pts, {len(c['ratings'])} levels")
        return

    print(f"Creating rubric '{args.title}' ({total} pts)...")
    out = request("POST", base, token, f"/courses/{args.course}/rubrics", fields)
    rubric = out.get("rubric", out)
    print(f"  rubric id={rubric.get('id')}")

    # The assignment ships with points_possible=0, which makes the gradebook
    # show 5/0 once scores sync. Match it to the rubric total.
    print(f"Setting assignment points_possible={total}...")
    request(
        "PUT", base, token,
        f"/courses/{args.course}/assignments/{args.assignment}",
        {"assignment[points_possible]": str(total)},
    )

    check = request("GET", base, token, f"/courses/{args.course}/assignments/{args.assignment}")
    print(f"\nVerified: '{check.get('name')}' now {check.get('points_possible')} pts")
    for c in check.get("rubric") or []:
        print(f"  {c['id']:<8} {c['description']:<22} {c['points']} pts, {len(c['ratings'])} levels")


if __name__ == "__main__":
    main()
