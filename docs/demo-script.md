# CourseCompass: 3-minute demo script

For the presenters. **Bold** lines are what you say; *italics* are what you click. Times are
targets, and the whole run fits in 3:00 with about 15 seconds of slack.

## Before you present (10 minutes ahead)

1. Start everything: `docker compose up -d`, the ML service, the backend (with `GEMINI_API_KEY` in
   `.env`), and the frontend. Check `/health` on the backend and ML service.
2. Seed a fresh demo account:
   ```bash
   python scripts/seed_demo.py --email demo-$(date +%H%M)@coursecompass.app
   ```
   Note the email it prints. The password is `wolfhacks-demo-2026`.
3. Log in once, open **CH 101** and **Insights**, then go back to the dashboard. This warms the
   caches so nothing loads slowly on stage.
4. Have these open in tabs: the app (logged in, on the dashboard), `notebooks/02_train_evaluate.ipynb`
   scrolled to the confusion matrix, and `docs/demo/ch101-syllabus.pdf` in Finder for the upload.
5. Zoom the browser to 110–125% so the room can read it.

Don't rate CH 101's items or press "Predict risk" while warming up. Those are the live moments.

---

## 0:00–0:20 · The problem

**"In the WolfHacks student dataset, more than 1 in 4 students (28%) finished their course with a D
or F. Often, by the time the grade shows up, the withdrawal deadline has passed. Students have to decide whether to stay or drop with almost no
information."**

**"CourseCompass gives them that information early: a risk prediction from a decision tree, their
own sense of how each exam went, and the syllabus weights, combined into one clear recommendation."**

*Show the dashboard.* **"Here's Jordan's semester. Calculus is on track. Chemistry looks
worrying."**

## 0:20–0:45 · Upload the syllabus

*Click **Upload** → choose **CH 101** → drag in `ch101-syllabus.pdf` → **Upload and extract text**.*

**"Jordan uploads the chemistry syllabus. We pull the text out of the PDF and save it to the
course."** (It shows "Saved 2,275 characters".)

**"That text now powers AI flashcards and practice quizzes. Every piece of AI-generated content is
labeled."** *(Optional, only if you're ahead of time: click **Make flashcards** and flip one card.)*

*Click **Course page**.*

## 0:45–1:25 · Rate how it went

**"These are the graded items from the syllabus, with their weights. Jordan got a 58 on the
midterm."**

*Drag **Final Exam** confidence to **3**, **Midterm** to **4**, **Lab reports** to **4**.*

**"Right after each exam, Jordan rates how confident they feel. Watch the projected grade: it
combines actual scores with those ratings, weighted by the syllabus. It just dropped from 63 to
55, which is below a C."**

**"The weekly check-in (attendance, on-time submissions, late-night studying) is already filled in
from last week."** *Scroll to it and click **Predict risk**.*

## 1:25–2:05 · The recommendation

*Point at the **Risk check** card.*

**"The decision tree says high risk, and it says why in plain English: midterm at or below 64.5,
on-time submissions at or below 79.5%, attendance at or below 94.5%. That's the exact path through the tree for
Jordan. It isn't a generic explanation."**

*Point at the recommendation card.*

**"Now the fusion. The model's risk, a projected grade of 55, only 25% of the grade still ahead,
and three low-confidence ratings add up to a strong signal to consider withdrawing. Gemini
rewrites the reasons in supportive language, and it's labeled AI-generated."**

*Point at the **Responsible AI** panel.*

**"And every card says it: this is guidance, not a verdict. The model was trained on synthetic data
and catches 77% of at-risk students. And always talk to your advisor before withdrawing."**

## 2:05–2:40 · The model

*Click **Insights** (use the notebook tab if a judge wants the code).*

**"The model is a depth-3 decision tree trained on the WolfHacks dataset with an 80/20 stratified
split. A baseline that calls everyone 'fine' gets 72.5% accuracy but catches zero at-risk
students. Our tree catches 77%, that's 34 of 44 in the held-out test set, at 79.4% accuracy and
0.87 ROC-AUC."**

*Point at the confusion matrix, then the importances.*

**"We tuned for recall because missing a struggling student costs more than a false alarm. Midterm
score drives 59% of the decisions, then on-time submissions and attendance. We chose depth 3 by
repeated cross-validation. Train and test accuracy are only 6.7 points apart, so it isn't
overfitting, and it's small enough to explain to a student."**

## 2:40–3:00 · Impact

**"Most tools show a dashboard or a model. CourseCompass connects them: the model says risk, you
say the midterm felt bad, the syllabus says what's left, and we turn that into a decision before
the deadline, plus flashcards, a quiz and a 7-day study plan if you stay."**

**"It's guidance, not a verdict. Thank you."**

---

## Numbers cheat sheet

| What | Value |
|---|---|
| Dataset | 800 synthetic students, 27.75% at risk, 80/20 stratified split (640 / 160) |
| Baseline (always "fine") | 72.5% accuracy, 0% recall |
| Decision tree (depth 3) | 79.4% accuracy, **77.3% recall (34 / 44)**, 59.6% precision, 0.87 ROC-AUC |
| Confusion matrix | TN 93 · FP 23 · FN 10 · TP 34 |
| Overfitting | Train 86.1% vs test 79.4% accuracy (gap 6.7 points); cross-validated recall ≈ 0.81 |
| Top features | Midterm 59% · on-time submissions 22% · attendance 11% · late-night studying 8% |
| Biggest at-risk path | Midterm ≤ 64.5, on-time ≤ 79.5%, attendance ≤ 94.5%: 110 of 139 training students were at risk |
| Demo course (CH 101) | Projected 63 → 55 after rating; 25% of grade remaining; strong withdraw signal |

## If something goes wrong

- **Gemini is slow or down:** the recommendation automatically falls back to the built-in wording
  (no "AI-generated" label). Say "the explanation falls back to our template if the LLM is
  unavailable" and keep going.
- **"You've used 10 AI generations…":** that's the per-user cap protecting the free tier. Skip the
  flashcards step, or seed a new account.
- **ML service down:** "Predict risk" shows an error. Use the Insights page and the notebook for
  the model part, and show the recommendation the seed already created.
- **Anything breaks on screen:** go to the notebook tab. The model section stands on its own.

## Likely judge questions

- **Why a decision tree?** It's required, and it's explainable: every prediction is a 3-step path
  we can show the student.
- **Why recall instead of accuracy?** Missing a struggling student costs more than a false alarm,
  and accuracy rewards ignoring the 28% minority.
- **Data leakage?** The split comes first. Imputation medians come from the training rows only,
  and the depth is chosen by cross-validation on training data only. The test set is used once.
- **Why don't sleep or study hours matter?** The tree never splits on them, because midterm,
  on-time submissions and attendance already carry that signal. We say so on the Insights page, and
  the app labels tips about those habits as general advice, not reasons for a flag.
- **Is the probability a real percentage?** No. The tree's leaf probabilities aren't calibrated, so
  the UI only shows high, moderate or low.
- **Privacy?** Only aggregate signals go to Gemini (no names or emails), and data is never shared.
