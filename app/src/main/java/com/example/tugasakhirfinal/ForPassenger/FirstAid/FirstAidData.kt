package com.example.tugasakhirfinal.ForPassenger.FirstAid

// Informasi umum untuk edukasi, BUKAN pengganti penanganan tenaga medis profesional.
object FirstAidData {

    fun getList(): List<FirstAidCondition> = listOf(
        FirstAidCondition(
            title = "Henti Jantung",
            symptoms = "Tidak sadarkan diri, tidak bernapas normal, tidak ada denyut nadi.",
            steps = listOf(
                "Segera hubungi ambulans / minta bantuan medis.",
                "Baringkan korban di permukaan yang datar dan keras.",
                "Lakukan Resusitasi Jantung Paru (RJP): tekan dada 100-120 kali/menit dengan kedalaman 5-6 cm.",
                "Gunakan AED (Automated External Defibrillator) jika tersedia.",
                "Lanjutkan RJP tanpa henti sampai bantuan medis datang atau korban sadar."
            )
        ),
        FirstAidCondition(
            title = "Serangan Jantung / Nyeri Dada Hebat",
            symptoms = "Nyeri atau rasa tertekan di dada, menjalar ke lengan/rahang, sesak napas, keringat dingin.",
            steps = listOf(
                "Segera hubungi ambulans.",
                "Dudukkan korban dalam posisi setengah duduk dan longgarkan pakaian yang ketat.",
                "Tenangkan korban, minta korban untuk tidak banyak bergerak.",
                "Jangan berikan makanan atau minuman apapun.",
                "Jika korban tidak sadar dan tidak bernapas, segera mulai RJP."
            )
        ),
        FirstAidCondition(
            title = "Stroke",
            symptoms = "Metode FAST: Face (wajah merot sebelah), Arm (lengan lemah/sulit diangkat), Speech (bicara pelo/tidak jelas), Time (segera cari bantuan).",
            steps = listOf(
                "Catat waktu gejala pertama kali muncul, sampaikan ke petugas medis.",
                "Segera hubungi ambulans.",
                "Baringkan korban dengan posisi kepala sedikit terangkat, miringkan jika muntah.",
                "Jangan berikan makan atau minum apapun (risiko tersedak).",
                "Jangan tinggalkan korban sendirian sampai bantuan datang."
            )
        ),
        FirstAidCondition(
            title = "Tersedak",
            symptoms = "Tiba-tiba tidak bisa bicara atau batuk, memegangi leher, wajah membiru.",
            steps = listOf(
                "Jika korban masih bisa batuk, dorong korban untuk terus batuk sekuat tenaga.",
                "Jika korban tidak bisa bernapas: lakukan hentakan perut (Heimlich maneuver) dari belakang korban.",
                "Untuk bayi: berikan pukulan punggung dan tekanan dada bergantian dengan posisi bayi tengkurap di lengan.",
                "Jika korban sampai tidak sadarkan diri, baringkan, mulai RJP, dan segera hubungi ambulans."
            )
        ),
        FirstAidCondition(
            title = "Pendarahan Hebat",
            symptoms = "Darah keluar deras dan tidak berhenti dari luka terbuka.",
            steps = listOf(
                "Tekan luka dengan kain atau kasa bersih sekuat mungkin secara terus-menerus.",
                "Jika memungkinkan, angkat bagian tubuh yang luka lebih tinggi dari posisi jantung.",
                "Jangan lepas kain penekan meski sudah basah darah - tambahkan lapisan baru di atasnya.",
                "Segera hubungi ambulans.",
                "Jika muncul tanda syok (pucat, lemas, keringat dingin), baringkan korban dan angkat kedua kakinya."
            )
        ),
        FirstAidCondition(
            title = "Luka Bakar",
            symptoms = "Kulit memerah, melepuh, atau menghitam pada area yang terbakar.",
            steps = listOf(
                "Alirkan air mengalir bersuhu ruang ke area luka selama 10-20 menit.",
                "Lepaskan perhiasan atau pakaian di sekitar area luka sebelum membengkak.",
                "Tutup longgar dengan kain bersih, jangan pecahkan lepuhan yang muncul.",
                "Jangan oleskan odol, mentega, atau es batu ke luka bakar.",
                "Segera bawa ke IGD untuk luka bakar yang luas atau dalam."
            )
        ),
        FirstAidCondition(
            title = "Kejang (Epilepsi)",
            symptoms = "Tubuh kaku atau menghentak-hentak, tidak sadar, mata mendelik.",
            steps = listOf(
                "Jauhkan benda-benda berbahaya di sekitar korban.",
                "Jangan menahan gerakan tubuh korban, jangan masukkan apapun ke dalam mulutnya.",
                "Letakkan sesuatu yang lembut di bawah kepala korban.",
                "Setelah kejang berhenti, miringkan tubuh korban (posisi pemulihan).",
                "Hubungi ambulans jika kejang berlangsung lebih dari 5 menit atau berulang."
            )
        ),
        FirstAidCondition(
            title = "Reaksi Alergi Berat (Anafilaksis)",
            symptoms = "Bengkak di wajah/tenggorokan, sesak napas, ruam mendadak setelah terpapar alergen.",
            steps = listOf(
                "Segera hubungi ambulans.",
                "Jika korban memiliki autoinjector epinefrin, bantu korban menggunakannya.",
                "Baringkan korban dengan kaki diangkat, kecuali jika sesak napas maka posisikan duduk.",
                "Longgarkan pakaian yang ketat.",
                "Pantau napas korban, siap melakukan RJP jika korban berhenti bernapas."
            )
        ),
        FirstAidCondition(
            title = "Keracunan",
            symptoms = "Mual, muntah, kejang, atau penurunan kesadaran setelah menelan/menghirup zat berbahaya.",
            steps = listOf(
                "Segera hubungi ambulans, informasikan zat yang tertelan/terhirup jika diketahui.",
                "Jangan memaksa korban muntah kecuali diarahkan oleh petugas medis.",
                "Jika racun mengenai kulit atau mata, bilas dengan air mengalir.",
                "Jika korban tidak sadar, baringkan dalam posisi miring (posisi pemulihan).",
                "Bawa kemasan atau sisa zat racun untuk ditunjukkan ke petugas medis."
            )
        )
    )
}
