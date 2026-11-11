package com.example.tugasakhirfinal.ForPassenger.FirstAid

// Panduan pertolongan pertama bawaan aplikasi (data awal untuk koleksi Firestore
// "firstAidConditions", diimpor/diperbarui Admin lewat ManageFirstAidActivity).
// Fokus pada kondisi gawat darurat yang paling sering ditemui dalam panggilan ambulans di
// Indonesia. Disusun dari pedoman pertolongan pertama umum (BLS/CPR untuk orang awam,
// FAST untuk stroke, penanganan perdarahan, dll.).
//
// PENTING: informasi umum untuk edukasi, BUKAN pengganti penanganan tenaga medis profesional.
// Setiap perubahan isi di sini: naikkan SEED_VERSION supaya Admin melihat tombol perbarui.
object FirstAidData {

    /** Naikkan setiap kali isi daftar di bawah berubah. Disimpan di dokumen sebagai "builtInVersion". */
    const val SEED_VERSION = 2

    fun getList(): List<FirstAidCondition> = listOf(
        FirstAidCondition(
            title = "Cardiac Arrest",
            symptoms = "Suddenly collapses, does not respond when tapped or called, not breathing or only gasping, no pulse.",
            steps = listOf(
                "Make sure the area is safe, then tap the shoulders and shout to check if the person responds.",
                "If there is no response and no normal breathing, call an ambulance now (Ambulansku or 119) and put the phone on speaker.",
                "Ask someone to look for an AED (automated external defibrillator) - many malls, airports and offices have one.",
                "Lay the person on their back on a firm surface. Place the heel of one hand in the center of the chest, the other hand on top.",
                "Push hard and fast: 5-6 cm deep, 100-120 times per minute, letting the chest rise fully between pushes.",
                "If you are trained, give 2 rescue breaths after every 30 compressions. If not, keep doing chest compressions only.",
                "Turn on the AED as soon as it arrives and follow its voice instructions.",
                "Do not stop until the person starts breathing normally, the ambulance team takes over, or you are too exhausted to continue (swap with someone every 2 minutes if possible)."
            )
        ),
        FirstAidCondition(
            title = "Heart Attack / Severe Chest Pain",
            symptoms = "Heavy, squeezing pain or pressure in the chest lasting more than a few minutes, may spread to the left arm, jaw, neck or back; shortness of breath, cold sweat, nausea, dizziness.",
            steps = listOf(
                "Call an ambulance immediately - do not drive the person yourself if an ambulance can come.",
                "Help them sit in a comfortable half-sitting position (leaning back with knees bent) and loosen tight clothing.",
                "If they are an adult, not allergic to aspirin and have no bleeding problems, give one 300 mg aspirin to chew slowly.",
                "If they have their own heart medication (e.g. nitroglycerin under the tongue), help them take it as prescribed.",
                "Keep them calm and still. Do not let them walk around or give them food.",
                "Stay with them and watch their breathing. If they become unresponsive and stop breathing normally, start CPR."
            )
        ),
        FirstAidCondition(
            title = "Stroke",
            symptoms = "Use BE-FAST: Balance loss, Eyes (sudden blurred or lost vision), Face drooping on one side, Arm or leg weakness on one side, Speech slurred or confused, Time - get help immediately.",
            steps = listOf(
                "Note the exact time the symptoms started (or when the person was last seen normal) - doctors need this to choose treatment.",
                "Call an ambulance immediately. Stroke treatment works best within the first few hours.",
                "Lay the person down with the head and shoulders slightly raised. If they are drowsy or vomiting, turn them on their side.",
                "Do not give food, drink or medicine (including aspirin) - swallowing may be affected and the stroke could be bleeding.",
                "Loosen tight clothing and keep them warm and calm.",
                "Do not leave them alone. If they stop breathing normally, start CPR."
            )
        ),
        FirstAidCondition(
            title = "Unconscious but Breathing",
            symptoms = "Does not respond to voice or touch but is breathing normally (chest rising and falling).",
            steps = listOf(
                "Call an ambulance.",
                "Check for breathing for up to 10 seconds by looking at the chest and listening near the mouth.",
                "If breathing normally and no spinal injury is suspected, put them in the recovery position: on their side, top leg bent, head tilted back so vomit can drain.",
                "If a neck or back injury is possible (fall, traffic accident), keep them still and only turn them if they vomit or the airway is blocked.",
                "Do not give anything by mouth.",
                "Keep checking breathing every minute. If breathing stops or becomes gasping, roll them onto their back and start CPR."
            )
        ),
        FirstAidCondition(
            title = "Choking",
            symptoms = "Suddenly cannot speak, cough or breathe; clutching the throat; face turning red then bluish; high-pitched noise when trying to breathe.",
            steps = listOf(
                "If the person can still cough strongly, encourage them to keep coughing. Do not pat their back yet.",
                "If they cannot cough, speak or breathe: stand slightly behind them, support the chest with one hand, lean them forward and give up to 5 firm back blows between the shoulder blades.",
                "If that does not work, give up to 5 abdominal thrusts (Heimlich): fist just above the navel, other hand over it, pull sharply inwards and upwards.",
                "Keep alternating 5 back blows and 5 abdominal thrusts until the object comes out. For pregnant or very large people, give chest thrusts instead of abdominal thrusts.",
                "Infant under 1 year: lay face-down along your forearm with the head lower, give 5 back blows, then turn face-up and give 5 chest thrusts with two fingers. Never use abdominal thrusts on infants.",
                "If the person becomes unresponsive, call an ambulance, lay them down and start CPR. Look in the mouth before rescue breaths and remove the object only if you can see it."
            )
        ),
        FirstAidCondition(
            title = "Severe Bleeding",
            symptoms = "Blood spurting or flowing heavily and continuously from a wound, blood soaking through cloth quickly.",
            steps = listOf(
                "Call an ambulance. Wear gloves or use a plastic bag over your hand if available.",
                "Press firmly and directly on the wound with a clean cloth, gauze or your hand. Keep pressing without lifting to check.",
                "If blood soaks through, do not remove the cloth - add more layers on top and press harder.",
                "If something is stuck in the wound, do not pull it out. Press around it and pad it in place.",
                "For heavy bleeding from an arm or leg that does not stop with pressure, tie a tourniquet (a wide strap or belt) 5-7 cm above the wound, tighten until the bleeding stops, and note the time it was applied.",
                "Lay the person down, keep them warm and watch for signs of shock (pale, cold, sweaty skin, fast breathing)."
            )
        ),
        FirstAidCondition(
            title = "Shock",
            symptoms = "Pale or grey, cold and sweaty skin; fast, weak pulse; fast shallow breathing; dizziness, confusion or drowsiness - often after heavy bleeding, severe burns, dehydration or a severe allergy.",
            steps = listOf(
                "Call an ambulance and treat the cause if you can (e.g. stop bleeding).",
                "Lay the person on their back. If there is no leg, hip or spine injury, raise both legs about 30 cm.",
                "Loosen tight clothing around the neck, chest and waist.",
                "Keep them warm with a blanket or jacket, but do not overheat.",
                "Do not give food or drink, even if they are thirsty - you may moisten their lips.",
                "Reassure them and check breathing and responsiveness regularly. Start CPR if they stop breathing normally."
            )
        ),
        FirstAidCondition(
            title = "Traffic Accident / Suspected Spine Injury",
            symptoms = "Motorcycle or car crash, fall from height, diving accident; neck or back pain, tingling or numbness, unable to move arms or legs.",
            steps = listOf(
                "Make the scene safe first: turn on hazard lights, warn other traffic, switch off engines. Do not become another victim.",
                "Call an ambulance and tell them how many people are injured.",
                "Do not move the injured person unless they are in immediate danger (fire, traffic, water).",
                "Keep the head and neck still in line with the body by holding both sides of the head with your hands.",
                "Do not remove a motorcycle helmet unless the person is not breathing and you must open the airway.",
                "Control any heavy bleeding with firm pressure, keep the person warm and talk to them calmly until help arrives.",
                "If they are not breathing normally, start CPR - breathing comes before protecting the spine."
            )
        ),
        FirstAidCondition(
            title = "Head Injury",
            symptoms = "Blow to the head; bump or wound on the scalp; headache, dizziness, confusion, vomiting, drowsiness, or blood/clear fluid from the nose or ears.",
            steps = listOf(
                "Call an ambulance if the person was knocked out, is confused, drowsy, vomiting repeatedly, has a seizure, or fluid is coming from the nose or ears.",
                "Keep them still and lying down with the head and shoulders slightly raised, unless a spine injury is suspected.",
                "Press gently on a bleeding scalp wound with a clean cloth. Do not press hard if the skull feels soft or dented.",
                "Hold a cold compress (ice wrapped in cloth) on a bump for 10-20 minutes.",
                "Do not give painkillers, food or alcohol until assessed by a doctor.",
                "Watch them closely for the next 24 hours - worsening headache, sleepiness or vomiting needs emergency care."
            )
        ),
        FirstAidCondition(
            title = "Fractures and Dislocations",
            symptoms = "Severe pain, swelling, bruising, a limb that looks bent or out of place, unable to move or put weight on it, bone visible through the skin.",
            steps = listOf(
                "Do not try to straighten the limb or push a bone back in.",
                "Keep the injured part still in the position you found it. Support it with padding, a folded jacket or a sling.",
                "If you need to move the person, splint the limb first: tie it gently to something rigid (board, rolled newspaper) above and below the injury.",
                "For an open fracture (bone through skin), cover the wound with a clean cloth and press around it, not on the bone.",
                "Apply a cold compress wrapped in cloth for up to 20 minutes to reduce swelling.",
                "Call an ambulance for thigh, hip, pelvis, spine or open fractures, or if the limb below the injury is pale, cold or numb."
            )
        ),
        FirstAidCondition(
            title = "Burns",
            symptoms = "Red, painful skin; blisters; white, leathery or blackened skin in deep burns.",
            steps = listOf(
                "Stop the burning: move away from the heat source, and for electrical burns switch off the power first.",
                "Cool the burn under cool running tap water for at least 20 minutes, as soon as possible.",
                "Remove jewelry, watches and clothing near the burn before swelling starts, unless they are stuck to the skin.",
                "Cover loosely with cling film or a clean, non-fluffy cloth. Do not pop blisters.",
                "Do not apply ice, toothpaste, butter, kecap or other home remedies.",
                "Call an ambulance for large burns, burns on the face, hands, feet, groin or joints, electrical or chemical burns, or burns in children and the elderly. Keep the person warm to prevent shock."
            )
        ),
        FirstAidCondition(
            title = "Breathing Difficulty / Asthma Attack",
            symptoms = "Struggling to breathe, wheezing, coughing, tight chest, unable to finish sentences, lips or fingertips turning blue.",
            steps = listOf(
                "Help the person sit upright and lean slightly forward. Do not lay them down.",
                "If they have a reliever inhaler (usually blue), help them take 1 puff every 30-60 seconds, up to 10 puffs, using a spacer if available.",
                "Loosen tight clothing and keep them calm. Breathe slowly with them.",
                "Call an ambulance if there is no inhaler, the inhaler does not help, they are too breathless to speak, or their lips turn blue.",
                "If the ambulance has not arrived after 15 minutes, repeat the 10 puffs.",
                "If they become unresponsive and stop breathing normally, start CPR."
            )
        ),
        FirstAidCondition(
            title = "Severe Allergic Reaction (Anaphylaxis)",
            symptoms = "Within minutes of food, medicine or an insect sting: swelling of the lips, tongue or throat, difficulty breathing, wheezing, widespread rash or hives, dizziness, fainting.",
            steps = listOf(
                "Call an ambulance immediately and say it is anaphylaxis.",
                "If the person has an adrenaline (epinephrine) auto-injector, help them use it on the outer thigh right away - it works through clothing.",
                "If breathing is difficult, let them sit up. If they feel faint, lay them down with legs raised. Do not make them stand or walk.",
                "If symptoms do not improve after 5 minutes and a second auto-injector is available, give it.",
                "Remove the trigger if possible (e.g. scrape away a bee sting).",
                "Watch breathing closely and start CPR if they stop breathing normally."
            )
        ),
        FirstAidCondition(
            title = "Seizure",
            symptoms = "Sudden stiffening and jerking of the body, loss of consciousness, eyes rolled back, foaming at the mouth, may wet themselves.",
            steps = listOf(
                "Stay calm and note the time the seizure starts.",
                "Move hard or sharp objects away and put something soft under the head.",
                "Do not hold them down and do not put anything in their mouth - they cannot swallow their tongue.",
                "When the jerking stops, turn them onto their side (recovery position) and check breathing.",
                "Stay with them and reassure them as they wake up; they may be confused for a while.",
                "Call an ambulance if it is their first seizure, it lasts more than 5 minutes, another seizure follows, they are injured, pregnant, diabetic, in water, or do not wake up."
            )
        ),
        FirstAidCondition(
            title = "High Fever and Febrile Seizure in Children",
            symptoms = "Temperature above 38°C; in young children (6 months to 5 years) a fever may cause a short seizure with stiffening, jerking and rolled-back eyes.",
            steps = listOf(
                "During a seizure: lay the child on a soft surface on their side, move objects away, do not put anything in the mouth and do not restrain them. Note how long it lasts.",
                "Do not put the child in cold water or wipe them with alcohol.",
                "After the seizure, remove extra clothing and keep the room cool. Give fever medicine (paracetamol) at the dose on the label once they are fully awake.",
                "Offer small, frequent drinks (breast milk, water) to prevent dehydration.",
                "Call an ambulance if the seizure lasts more than 5 minutes, it is the first one, the child has trouble breathing, a stiff neck, a rash that does not fade when pressed, or is hard to wake.",
                "Any baby under 3 months with a fever should see a doctor the same day."
            )
        ),
        FirstAidCondition(
            title = "Low Blood Sugar (Hypoglycemia)",
            symptoms = "Usually in people with diabetes: shaking, sweating, pale skin, hunger, fast heartbeat, confusion, irritability, slurred speech, drowsiness.",
            steps = listOf(
                "If the person is awake and can swallow, give 15-20 g of fast sugar: half a glass of sweet tea or fruit juice, regular (not diet) soft drink, 3-4 teaspoons of sugar or candies.",
                "Let them sit down and rest. Check again after 15 minutes - if they still feel unwell, give the same amount of sugar again.",
                "Once they feel better, give a snack with longer-lasting energy (bread, biscuits, rice).",
                "If they become unresponsive or cannot swallow safely, do not put food or drink in the mouth - call an ambulance and place them in the recovery position.",
                "Call an ambulance if they do not improve after two doses of sugar."
            )
        ),
        FirstAidCondition(
            title = "Heat Stroke",
            symptoms = "After heat exposure or heavy exercise: very high body temperature, hot red skin (may be dry), headache, confusion, staggering, vomiting, seizures or collapse.",
            steps = listOf(
                "Call an ambulance - heat stroke is life-threatening.",
                "Move the person to a cool, shaded place and remove excess clothing.",
                "Cool them quickly: spray or pour cool water over the body and fan them; place cold packs or wet cloths on the neck, armpits and groin.",
                "If they are fully awake and can swallow, give small sips of cool water or an oral rehydration drink.",
                "Do not give paracetamol or aspirin - they do not lower heat stroke temperature.",
                "If they become unresponsive, put them in the recovery position and be ready to start CPR."
            )
        ),
        FirstAidCondition(
            title = "Severe Dehydration (Diarrhea and Vomiting)",
            symptoms = "Frequent watery diarrhea or vomiting; very thirsty, dry mouth, little or no urine, sunken eyes, weakness, dizziness; in babies a sunken soft spot and no tears.",
            steps = listOf(
                "Give oral rehydration solution (oralit) in small, frequent sips - a spoonful every few minutes if vomiting.",
                "If oralit is not available, mix 1 liter of boiled water with 6 level teaspoons of sugar and half a level teaspoon of salt.",
                "Keep breastfeeding babies; do not give soft drinks or very sweet juice.",
                "Children can be given zinc tablets as advised by a health worker.",
                "Call an ambulance or go to the emergency room if the person is drowsy or confused, cannot drink, has blood in the stool, has not urinated for 8 hours (6 hours for babies), or keeps vomiting everything."
            )
        ),
        FirstAidCondition(
            title = "Dengue Fever Warning Signs (DBD)",
            symptoms = "High fever for 2-7 days with headache, pain behind the eyes, muscle and joint pain, rash. Danger signs usually appear as the fever drops (day 3-7).",
            steps = listOf(
                "Rest and drink plenty of fluids: water, oralit, fruit juice, soup.",
                "For fever and pain use paracetamol only. Do not give ibuprofen, aspirin or other NSAIDs - they increase the risk of bleeding.",
                "Watch closely for danger signs, especially when the fever goes down: severe stomach pain, repeated vomiting, bleeding gums or nose, black stool, vomiting blood, cold clammy skin, restlessness or drowsiness.",
                "If any danger sign appears, call an ambulance or go to the emergency room immediately - this can progress to dengue shock.",
                "Have a blood test done at a clinic or hospital to confirm dengue and check platelet levels."
            )
        ),
        FirstAidCondition(
            title = "Poisoning and Drug Overdose",
            symptoms = "Nausea, vomiting, stomach pain, drowsiness, confusion, seizures, difficulty breathing or unconsciousness after swallowing, inhaling or touching a harmful substance or too much medicine.",
            steps = listOf(
                "Call an ambulance and, if you can, tell them what was taken, how much and when.",
                "Do not make the person vomit and do not give milk, salt water or anything else unless instructed by medical staff.",
                "For inhaled poisons (gas, smoke, pesticide fumes), move them to fresh air if it is safe for you.",
                "For chemicals on the skin or in the eyes, remove contaminated clothing and rinse with running water for at least 20 minutes.",
                "If they are unresponsive but breathing, place them in the recovery position. If not breathing normally, start CPR (avoid mouth-to-mouth if poison is around the mouth).",
                "Bring the container, pill pack or a photo of the label to the hospital."
            )
        ),
        FirstAidCondition(
            title = "Snake Bite",
            symptoms = "Fang marks, pain and swelling at the bite; may cause drooping eyelids, difficulty swallowing or breathing, bleeding, nausea, weakness.",
            steps = listOf(
                "Move the person away from the snake. Do not try to catch or kill it - take a photo from a safe distance if possible.",
                "Call an ambulance and keep the person calm and as still as possible; moving spreads the venom.",
                "Remove rings, watches and tight clothing near the bite before swelling starts.",
                "Immobilize the bitten limb with a splint like a fracture and keep it at about heart level.",
                "Do not cut the wound, suck out the venom, apply a tight tourniquet, ice, herbs or electric shock.",
                "Note the time of the bite and watch breathing. Start CPR if they stop breathing normally."
            )
        ),
        FirstAidCondition(
            title = "Animal Bite (Dog, Cat, Monkey)",
            symptoms = "Bite or deep scratch that breaks the skin; risk of rabies and infection.",
            steps = listOf(
                "Wash the wound immediately with soap and running water for at least 15 minutes - this greatly reduces the risk of rabies.",
                "Apply an antiseptic (povidone-iodine or alcohol) after washing.",
                "Control bleeding with gentle pressure and cover with a clean dressing. Do not stitch or tightly close the wound yourself.",
                "Go to a health facility the same day for rabies vaccination (VAR) and tetanus assessment, even if the wound looks small.",
                "Call an ambulance for severe bleeding, deep bites on the face, neck or hands, or multiple bites.",
                "If possible, note the animal and report it so it can be observed for 10-14 days."
            )
        ),
        FirstAidCondition(
            title = "Drowning",
            symptoms = "Pulled from water; coughing, vomiting, difficulty breathing, bluish lips, or unresponsive and not breathing.",
            steps = listOf(
                "Do not enter the water unless you are trained - throw a rope, float or reach with a stick instead.",
                "Call an ambulance once the person is out of the water.",
                "If they are not breathing normally, start CPR right away. If you are trained, give 5 rescue breaths first, then 30 compressions and 2 breaths.",
                "Do not try to press water out of the stomach - it wastes time and can cause vomiting.",
                "If they are breathing, place them in the recovery position, remove wet clothes and keep them warm.",
                "Everyone who nearly drowned should be checked at a hospital, even if they seem fine - breathing problems can appear hours later."
            )
        ),
        FirstAidCondition(
            title = "Electric Shock",
            symptoms = "Contact with electricity or lightning; burns at entry and exit points, muscle pain, confusion, irregular heartbeat, unconsciousness.",
            steps = listOf(
                "Do not touch the person while they are still in contact with the electric source.",
                "Switch off the power at the main switch (MCB) or unplug it. If that is not possible, push the source away with a dry non-metal object (wooden broom, plastic chair) while standing on something dry.",
                "For fallen high-voltage power lines, stay at least 10 meters away and call PLN and an ambulance.",
                "Once safe, check responsiveness and breathing. Start CPR if they are not breathing normally.",
                "Cool burns with running water for 20 minutes and cover loosely.",
                "Every person who had an electric shock should be checked by a doctor - the heart rhythm can be affected."
            )
        ),
        FirstAidCondition(
            title = "Fainting",
            symptoms = "Feeling lightheaded, pale, sweaty, blurred vision, then brief loss of consciousness and falling; usually wakes up within 1-2 minutes.",
            steps = listOf(
                "If they feel faint, help them lie down or sit with their head between their knees.",
                "If they have fainted, lay them on their back and raise their legs about 30 cm.",
                "Loosen tight clothing and make sure there is fresh air - ask bystanders to step back.",
                "When they wake, keep them lying down for a few minutes and help them sit up slowly.",
                "If they do not wake within 1-2 minutes, put them in the recovery position and call an ambulance.",
                "Call an ambulance if fainting happened during exercise, with chest pain or a racing heart, in pregnancy, after a head injury, or in someone over 50."
            )
        ),
        FirstAidCondition(
            title = "Emergency Childbirth",
            symptoms = "Strong contractions every 2-3 minutes, an urge to push, the baby's head visible at the vaginal opening.",
            steps = listOf(
                "Call an ambulance and tell them the baby is coming. Wash your hands and prepare clean towels or cloth.",
                "Help the mother into a comfortable position (half-sitting or lying on her side) on a clean surface.",
                "Do not pull the baby. Support the head gently with your hands as it comes out; the body usually follows with the next contraction.",
                "If the cord is wrapped around the neck, gently slip it over the head. Do not cut the cord - leave that to medical staff.",
                "Dry the baby straight away, place it skin-to-skin on the mother's chest and cover both with a dry cloth. Wipe mucus from the mouth and nose.",
                "If the baby does not breathe or cry, rub its back. If still not breathing, start infant CPR.",
                "Heavy bleeding after the birth is an emergency: massage the mother's lower belly firmly and let the baby breastfeed while waiting for the ambulance."
            )
        ),
        FirstAidCondition(
            title = "Bleeding in Pregnancy",
            symptoms = "Vaginal bleeding during pregnancy, with or without stomach pain or cramps; may be accompanied by dizziness or fainting.",
            steps = listOf(
                "Call an ambulance, especially if the bleeding is heavy, there is severe stomach pain, or she feels faint.",
                "Help her lie down on her left side and keep her calm and warm.",
                "Use a clean pad to track how much blood is lost. Do not put anything into the vagina.",
                "Do not give food or drink in case surgery is needed.",
                "Bring her pregnancy record book (buku KIA) to the hospital if available.",
                "Watch for signs of shock (pale, cold, sweaty skin, fast breathing) and raise her legs if they appear."
            )
        ),
        FirstAidCondition(
            title = "Eye Injury and Chemicals in the Eye",
            symptoms = "Pain, redness, watering, blurred vision after a chemical splash, flying object or blow to the eye.",
            steps = listOf(
                "For chemicals: rinse the eye immediately with clean running water for at least 20 minutes, holding the eyelids open and tilting the head so water runs away from the other eye.",
                "Remove contact lenses if they come out easily.",
                "Do not rub the eye or try to remove an object stuck in the eyeball.",
                "For an object stuck in the eye, cover both eyes loosely (e.g. with a paper cup over the injured eye) to stop eye movement.",
                "For a blow to the eye, apply a cold compress gently without pressing on the eyeball.",
                "Go to the emergency room for any chemical splash, embedded object, cut to the eye or sudden loss of vision."
            )
        )
    )
}
