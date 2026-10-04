# MiAdvisor: 3-minute demo script

For the presenters. **Bold** lines are what you say; *italics* are what you click. Times are
targets, and the whole run fits in 3:00 with about 10 seconds of slack. Everything runs locally
on the presenting laptop.

## Before you present (10 minutes ahead)

1. Start everything (see "Running it locally" in the README): `docker compose up -d`, the ML
   service, the backend (`set -a; source .env; set +a` first, so `GEMINI_API_KEY` and
   `ELEVENLABS_API_KEY` load), and the frontend. Check `/health` on ports 8000 and 8080.
2. Seed a fresh demo account:
   ```bash
   python3 scripts/seed_demo.py --email demo-$(date +%H%M)@miadvisor.app
   ```
   Note the email it prints. The password is `wolfhacks-demo-2026`.
3. Log in once, open **MA 241** and **Insights**, then go back to the dashboard. This warms the
   caches so nothing loads slowly on stage. On MA 241, click **🔊 Listen to your briefing** once
   to confirm the voice works; replays are free.
4. Have these open in tabs: the app (logged in, on the dashboard), `notebooks/02_train_evaluate.ipynb`
   scrolled to the confusion matrix, and `docs/demo/ch101-syllabus.pdf` in Finder for the upload.
5. Zoom the browser to 110–125% so the room can read it, and turn the laptop volume up for the
   briefing.

Don't rate CH 101's items, press "Predict risk" or play CH 101's briefing while warming up. Those
are the live moments.

---

## 0:00–0:15 · The problem

**"In the WolfHacks dataset, more than 1 in 4 students (28%) finished with a D or F, and often the
withdrawal deadline had already passed by the time the grade showed up. MiAdvisor tells students
early, explains why, and helps them decide whether to stay or withdraw."**

*Show the dashboard.* **"Here's Jordan's semester. Calculus is on track. Chemistry looks
worrying."**

## 0:15–0:35 · Upload the syllabus

*Click **Upload** → choose **CH 101** → drag in `ch101-syllabus.pdf` → **Upload and extract text**.*

**"Jordan uploads the chemistry syllabus. That text powers Gemini-generated flashcards and
practice quizzes, and every piece of AI content is labeled."**

*Click **Course page**.*

## 0:35–1:05 · Rate how it went

**"These are the graded items, with their weights from the syllabus. Jordan got a 58 on the
midterm."**

*Drag **Final Exam** confidence to **3**, **Midterm** to **4**, **Lab reports** to **4**.*

**"After each exam, Jordan rates how confident they feel. The projected grade combines real scores
with those ratings: it just dropped from 63 to 55, below a C. To finish with a C, they'd need 92%
on the 25% of the grade that's left."**

*Scroll to the weekly check-in (already filled in) and click **Predict risk**.*

## 1:05–1:40 · The recommendation

*Point at the **Risk check** card.*

**"The decision tree says high risk, and shows Jordan's exact path through the tree: midterm at
or below 64.5, on-time submissions at or below 79.5%, attendance at or below 94.5%."**

*Point at the recommendation card.*

**"We combine the model's risk, the projected 55, the steep 92% climb and three low ratings into
one recommendation: a strong signal to consider withdrawing. Gemini rewrites the reasons in
supportive language, labeled AI-generated, and it can't add numbers that aren't in the data."**

## 1:40–2:10 · Listen to the briefing

*Click **🔊 Listen to your briefing** (it takes about 5 seconds to prepare). Let it play for
about 15 seconds, then pause.*

**"Not every student wants to read a risk report at midnight. Gemini writes a 30-second briefing
from the same data, and ElevenLabs reads it in a calm, supportive voice. The ending is added by
our code, not the AI: this is guidance, not a verdict, and talk to your advisor before
withdrawing."**

*Open **Read the transcript** to show it matches the card.*

## 2:10–2:40 · The model

*Click **Insights** (use the notebook tab if a judge wants the code).*

**"A baseline that calls everyone 'fine' gets 72.5% accuracy but catches zero at-risk students.
Our depth-3 tree catches 77%, 34 of 44 in the held-out test set, at 79.4% accuracy and 0.87
ROC-AUC. We tuned for recall because missing a struggling student costs more than a false alarm.
Midterm score drives 59% of the decisions, and train and test accuracy are only 6.7 points apart,
so it isn't overfitting."**

## 2:40–3:00 · Impact

**"MiAdvisor connects the model, the student's own sense of how it's going, and the syllabus, and
turns them into a decision before the deadline, with a voice briefing, flashcards, a quiz and a
study plan if they stay. It's guidance, not a verdict. Thank you."**

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
| Demo course (CH 101) | Projected 63 → 55 after rating; needs 92% on the remaining 25% for a C (63% average so far); strong withdraw signal |
| Voice briefing | About 25–30 seconds; Gemini script (`gemini-3.5-flash-lite`), ElevenLabs voice "Sarah" (`eleven_multilingual_v2`) |

## If something goes wrong

- **Gemini is slow or down:** the recommendation and briefing fall back to built-in template
  wording (the recommendation loses its "AI-generated" label). Say "it falls back to our template
  if the LLM is unavailable" and keep going.
- **The briefing button is missing:** the backend didn't load `ELEVENLABS_API_KEY`. Skip the
  briefing and play MA 241's briefing later if there's time, or describe it.
- **"The voice briefing didn't work this time":** ElevenLabs credits or network. Click once more;
  if it fails again, open MA 241 and play the briefing you warmed up (replays are cached).
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
- **How do you decide whether recovery is realistic?** From actual grades, not ratings: the average
  needed on everything left to finish at 70. Above 85% counts as a steep climb; above 100% means a C
  is out of reach, and the app never recommends staying in that case.
- **Is the probability a real percentage?** No. The tree's leaf probabilities aren't calibrated, so
  the UI only shows high, moderate or low.
- **How do you stop the AI making things up?** Gemini only rewords decisions made by the tree and
  our rules. Any output that adds a number not in its input is rejected and replaced by template
  text, and the voice briefing's disclaimer is appended by code.
- **Why a voice briefing?** It's more accessible, and a calm voice lands better than a red badge
  when the news is bad. The transcript is always one click away.
- **Privacy?** Only course names and aggregate grade and risk figures go to Gemini and ElevenLabs
  (no names or emails), and data is never shared.
