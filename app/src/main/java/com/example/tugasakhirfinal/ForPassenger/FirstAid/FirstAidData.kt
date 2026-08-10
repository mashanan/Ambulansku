package com.example.tugasakhirfinal.ForPassenger.FirstAid

// Informasi umum untuk edukasi, BUKAN pengganti penanganan tenaga medis profesional.
object FirstAidData {

    fun getList(): List<FirstAidCondition> = listOf(
        FirstAidCondition(
            title = "Cardiac Arrest",
            symptoms = "Unconscious, not breathing normally, no pulse.",
            steps = listOf(
                "Call an ambulance / seek medical help immediately.",
                "Lay the victim on a flat, firm surface.",
                "Perform CPR (Cardiopulmonary Resuscitation): compress the chest 100-120 times/minute at a depth of 5-6 cm.",
                "Use an AED (Automated External Defibrillator) if available.",
                "Continue CPR without stopping until medical help arrives or the victim regains consciousness."
            )
        ),
        FirstAidCondition(
            title = "Heart Attack / Severe Chest Pain",
            symptoms = "Pain or pressure in the chest, radiating to the arm/jaw, shortness of breath, cold sweat.",
            steps = listOf(
                "Call an ambulance immediately.",
                "Sit the victim in a half-sitting position and loosen any tight clothing.",
                "Keep the victim calm and ask them to avoid moving too much.",
                "Do not give any food or drink.",
                "If the victim is unconscious and not breathing, start CPR immediately."
            )
        ),
        FirstAidCondition(
            title = "Stroke",
            symptoms = "FAST method: Face (drooping on one side), Arm (weakness/difficulty raising an arm), Speech (slurred or unclear speech), Time (seek help immediately).",
            steps = listOf(
                "Note the time symptoms first appeared and report it to medical staff.",
                "Call an ambulance immediately.",
                "Lay the victim down with the head slightly raised, turn them on their side if vomiting.",
                "Do not give any food or drink (risk of choking).",
                "Do not leave the victim alone until help arrives."
            )
        ),
        FirstAidCondition(
            title = "Choking",
            symptoms = "Suddenly unable to speak or cough, clutching the throat, bluish face.",
            steps = listOf(
                "If the victim can still cough, encourage them to keep coughing as hard as possible.",
                "If the victim cannot breathe: perform abdominal thrusts (Heimlich maneuver) from behind the victim.",
                "For infants: alternate back blows and chest thrusts with the infant face-down along your arm.",
                "If the victim becomes unconscious, lay them down, start CPR, and call an ambulance immediately."
            )
        ),
        FirstAidCondition(
            title = "Severe Bleeding",
            symptoms = "Heavy, continuous bleeding from an open wound.",
            steps = listOf(
                "Apply firm, continuous pressure to the wound with a clean cloth or gauze.",
                "If possible, raise the injured body part above heart level.",
                "Do not remove the pressure cloth even if soaked with blood - add a new layer on top.",
                "Call an ambulance immediately.",
                "If signs of shock appear (paleness, weakness, cold sweat), lay the victim down and raise both legs."
            )
        ),
        FirstAidCondition(
            title = "Burns",
            symptoms = "Red, blistered, or blackened skin on the burned area.",
            steps = listOf(
                "Run room-temperature water over the wound for 10-20 minutes.",
                "Remove jewelry or clothing around the wound area before swelling starts.",
                "Cover loosely with a clean cloth; do not pop any blisters that form.",
                "Do not apply toothpaste, butter, or ice to the burn.",
                "Seek emergency care immediately for extensive or deep burns."
            )
        ),
        FirstAidCondition(
            title = "Seizure (Epilepsy)",
            symptoms = "Body stiff or jerking, unconscious, eyes rolled back.",
            steps = listOf(
                "Move dangerous objects away from the victim.",
                "Do not restrain the victim's movements or put anything in their mouth.",
                "Place something soft under the victim's head.",
                "Once the seizure stops, turn the victim onto their side (recovery position).",
                "Call an ambulance if the seizure lasts longer than 5 minutes or recurs."
            )
        ),
        FirstAidCondition(
            title = "Severe Allergic Reaction (Anaphylaxis)",
            symptoms = "Swelling of the face/throat, shortness of breath, sudden rash after exposure to an allergen.",
            steps = listOf(
                "Call an ambulance immediately.",
                "If the victim has an epinephrine auto-injector, help them use it.",
                "Lay the victim down with legs raised, unless they have trouble breathing, in which case sit them up.",
                "Loosen any tight clothing.",
                "Monitor the victim's breathing and be ready to perform CPR if they stop breathing."
            )
        ),
        FirstAidCondition(
            title = "Poisoning",
            symptoms = "Nausea, vomiting, seizures, or reduced consciousness after swallowing/inhaling a harmful substance.",
            steps = listOf(
                "Call an ambulance immediately and report the substance ingested/inhaled if known.",
                "Do not induce vomiting unless instructed by medical personnel.",
                "If the poison contacted skin or eyes, rinse with running water.",
                "If the victim is unconscious, lay them on their side (recovery position).",
                "Bring the container or remaining substance to show medical personnel."
            )
        )
    )
}
