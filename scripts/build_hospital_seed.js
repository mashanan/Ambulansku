// Cleans the raw Google Places results (scripts/hospitals_jatim.json + scripts/hospitals_jawa.json)
// into the seed file the app imports: app/src/main/assets/hospitals_seed.json.
// Usage (from repo root):  node scripts/build_hospital_seed.js
const fs = require("fs");
const path = require("path");

const readRaw = f => JSON.parse(fs.readFileSync(path.join(__dirname, f), "utf8"));
const jatim = readRaw("hospitals_jatim.json").map(h => ({ ...h, province: "Jawa Timur" }));
const jawa = readRaw("hospitals_jawa.json");

// Entries where the first Places match was a sub-building or lacked a phone: replaced by a better
// listing found with an alternative query (same hospital).
const REPLACE = {
  "RSUD Kota Salatiga": { placeId: "ChIJPey86f15ei4RK-MafH_UZSM", nama: "RSUD dr. Soebarkat Tjitrodarmodjo Kota Salatiga", alamat: "Jl. Osamaliki No.19, Mangunsari, Kec. Sidomukti, Kota Salatiga, Jawa Tengah", telepon: "(0298) 324074", latitude: -7.3271696, longitude: 110.4954802, isHospital: true },
  "RSUD Karanganyar": { placeId: "ChIJ5cC2SXwYei4RIJ7-ghG-OLc", nama: "RSUD Kartini Karanganyar", telepon: "(0271) 495673", latitude: -7.5911681, longitude: 110.9500342, isHospital: true },
  "RSUD Djojonegoro Temanggung": { placeId: "ChIJVVi34Hl4cC4RnGnE603xlhI", nama: "RSUD Kabupaten Temanggung", alamat: "Jl. Gajah Mada No.1A, Tepungsari, Temanggung I, Kec. Temanggung, Kabupaten Temanggung, Jawa Tengah", telepon: "(0293) 491118", latitude: -7.3094297, longitude: 110.176738, isHospital: true },
};
for (const h of jawa) if (REPLACE[h.query]) Object.assign(h, REPLACE[h.query]);
// RSUD Dr. Soekardjo is a real hospital, Places just doesn't tag it with the "hospital" type.
for (const h of jawa) if (h.query === "RSUD dr. Soekardjo Tasikmalaya") h.isHospital = true;

const raw = [...jatim, ...jawa];

// Consistent display names ("<type> <name> <city>") instead of the mixed Google listing titles.
const NAMES = {
  // DKI Jakarta & Banten
  "Rumah Sakit Nasional Dr. Cipto Mangunkusumo": "RSUPN Dr. Cipto Mangunkusumo",
  "RS Nasional Dr. Cipto Mangunkusumo": "RSUPN Dr. Cipto Mangunkusumo",
  "RS Fatmawati": "RSUP Fatmawati",
  "Rumah Sakit Fatmawati": "RSUP Fatmawati",
  "Pusat Jantung Nasional Harapan Kita": "RS Jantung Harapan Kita",
  "RS BHAYANGKARA TK.I PUSDOKKES POLRI": "RS Bhayangkara Tk. I Polri Kramat Jati",
  "Rumah Sakit Pondok Indah Pondok Indah": "RS Pondok Indah",
  "RS Pondok Indah Pondok Indah": "RS Pondok Indah",
  "Mayapada Hospital Jakarta Selatan (MHJS)": "Mayapada Hospital Lebak Bulus",
  "Hermina Hospital Jatinegara": "RS Hermina Jatinegara",
  "Siloam International Hospital Lippo Village": "Siloam Hospitals Lippo Village",
  "Eka Hospital": "Eka Hospital BSD",
  "RSUD Dr. Drajat Prawiranegara": "RSUD dr. Dradjat Prawiranegara Serang",
  "IHC RS. Krakatau Medika": "RS Krakatau Medika Cilegon",
  "RSUD Dr. Adjidarmo Rangkasbitung": "RSUD dr. Adjidarmo Lebak",
  // Jawa Barat
  "RS Santo Borromeus": "RS Santo Borromeus Bandung",
  "Rumah Sakit Santo Borromeus": "RS Santo Borromeus Bandung",
  "RSAI Bandung": "RS Al Islam Bandung",
  "RSUD Welas Asih": "RSUD Welas Asih (Al Ihsan) Kabupaten Bandung",
  "RS Dustira": "RS Dustira Cimahi",
  "Rumah Sakit Dustira": "RS Dustira Cimahi",
  "RSUD CIBINONG KABUPATEN BOGOR": "RSUD Cibinong Kabupaten Bogor",
  "RSUD DR. K.H. Idham Chalid (Ciawi)": "RSUD dr. K.H. Idham Chalid Ciawi Bogor",
  "Mitra Keluarga Bekasi": "RS Mitra Keluarga Bekasi Barat",
  "RSUD KARAWANG": "RSUD Karawang",
  "RSUD Umar Wirahadikusumah": "RSUD Umar Wirahadikusumah Sumedang",
  "RSUD R. Syamsudin, SH. (Bunut)": "RSUD R. Syamsudin, SH Kota Sukabumi",
  "RSUD kota banjar": "RSUD Kota Banjar",
  "RS Daerah Gunung Jati Kota Cirebon": "RSD Gunung Jati Kota Cirebon",
  "Rumah Sakit Daerah Gunung Jati Kota Cirebon": "RSD Gunung Jati Kota Cirebon",
  "RSUD Kabupaten Indramayu": "RSUD Indramayu",
  "RSUD Majalengka Kabupaten Majalengka": "RSUD Majalengka",
  "RSUD \"45\" Kuningan": "RSUD 45 Kuningan",
  // Jawa Tengah
  "RSUP Dr Kariadi Semarang": "RSUP Dr. Kariadi Semarang",
  "RSD K.R.M.T. Wongsonegoro Kota Semarang": "RSUD K.R.M.T. Wongsonegoro Semarang",
  "RSUD Dr. Adhyatma, MPH Tugurejo (RSUD Jawa Tengah)": "RSUD Dr. Adhyatma Tugurejo Semarang",
  "SMC RS Telogorejo Semarang": "SMC RS Telogorejo Semarang",
  "RS St. Elisabeth": "RS St. Elisabeth Semarang",
  "Rumah Sakit St. Elisabeth": "RS St. Elisabeth Semarang",
  "RS ROEMANI MUHAMMADIYAH": "RS Roemani Muhammadiyah Semarang",
  "Rumah Sakit ROEMANI MUHAMMADIYAH": "RS Roemani Muhammadiyah Semarang",
  "RSUD dr. Gondo Suwarno Ungaran": "RSUD dr. Gondo Suwarno Ungaran",
  "RSUD Sunan Kalijaga Kabupaten Demak": "RSUD Sunan Kalijaga Demak",
  "RSUD. Dr. R Soedjati Purwodadi Grobogan": "RSUD dr. R. Soedjati Purwodadi Grobogan",
  "RSUD R.A. Kartini Jepara Official": "RSUD R.A. Kartini Jepara",
  "RSUD dr. R. SOETIJONO BLORA": "RSUD dr. R. Soetijono Blora",
  "RSUD dr. Moewardi": "RSUD Dr. Moewardi Surakarta",
  "Rumah Sakit Dr. OEN KANDANG SAPI SOLO": "RS Dr. Oen Kandang Sapi Solo",
  "RS Dr. OEN KANDANG SAPI SOLO": "RS Dr. Oen Kandang Sapi Solo",
  "RSUD Ir.SOEKARNO KABUPATEN SUKOHARJO": "RSUD Ir. Soekarno Sukoharjo",
  "RSUD dr.Soehadi Prijonegoro Sragen": "RSUD dr. Soehadi Prijonegoro Sragen",
  "RSUD Muntilan Kabupaten Magelang": "RSUD Muntilan Magelang",
  "RSUD KRT. Setjonegoro Wonosobo": "RSUD KRT Setjonegoro Wonosobo",
  "RSUD Prof. Dr. Margono Soekarjo": "RSUD Prof. Dr. Margono Soekarjo Purwokerto",
  "RSUD dr. R. Goeteng Taroenadibrata": "RSUD dr. R. Goeteng Taroenadibrata Purbalingga",
  "RSUD Hj. ANNA LASMANAH BANJARNEGARA": "RSUD Hj. Anna Lasmanah Banjarnegara",
  "RSUD Kardinah, Kota Tegal": "RSUD Kardinah Kota Tegal",
  "RSUD dr. Soeselo Kabupaten Tegal": "RSUD dr. Soeselo Slawi Tegal",
  "RSUD Bendan Kota Pekalongan": "RSUD Bendan Kota Pekalongan",
  "RSUD Kraton Kab. Pekalongan": "RSUD Kraton Kabupaten Pekalongan",
  "RSUD Batang Jawa Tengah": "RSUD Batang",
  // DI Yogyakarta
  "RSUP Dr. Sardjito": "RSUP Dr. Sardjito Yogyakarta",
  "RS Panti Rapih": "RS Panti Rapih Yogyakarta",
  "Rumah Sakit Panti Rapih": "RS Panti Rapih Yogyakarta",
  "RS JIH": "RS JIH Yogyakarta",
  "Rumah Sakit JIH": "RS JIH Yogyakarta",
  "RSUD Panembahan Senopati": "RSUD Panembahan Senopati Bantul",
  "Rumah Sakit Umum Daerah ( RSUD ) Wates": "RSUD Wates Kulon Progo",
  "Rumah Sakit Umum Daerah ( RSUD ) Wonosari": "RSUD Wonosari Gunungkidul",
  "RSUD ( RSUD ) Wates": "RSUD Wates Kulon Progo",
  "RSUD ( RSUD ) Wonosari": "RSUD Wonosari Gunungkidul",
  "RSUD Dr. Saiful Anwar": "RSUD Dr. Saiful Anwar Malang",
  "IHC Rumah Sakit Lavalette": "RS Lavalette Malang",
  "Rumah Sakit Panti Nirmala": "RS Panti Nirmala Malang",
  "Rumah Sakit Panti Waluya Sawahan": "RS Panti Waluya Sawahan Malang",
  "Rumah Sakit Universitas Brawijaya": "RS Universitas Brawijaya Malang",
  "RSI UNISMA": "RSI Unisma Malang",
  "Persada Hospital": "Persada Hospital Malang",
  "Hermina Hospital Tangkubanprahu": "RS Hermina Tangkubanprahu Malang",
  "RSUD KANJURUHAN KAB. MALANG": "RSUD Kanjuruhan Kabupaten Malang",
  "RS Wava Husada": "RS Wava Husada Kepanjen",
  "Rumah Sakit Baptis Batu": "RS Baptis Batu",
  "Rumah Sakit Delta Surya": "RS Delta Surya Sidoarjo",
  "Mitra Keluarga Waru": "RS Mitra Keluarga Waru Sidoarjo",
  "RSUD SIDOARJO BARAT": "RSUD Sidoarjo Barat (Krian)",
  "Rumah Sakit Semen Gresik": "RS Semen Gresik",
  "Rumah Sakit Petrokimia Gresik": "RS Petrokimia Gresik",
  "RSUD Prof.dr. Soekandar Mojosari": "RSUD Prof. dr. Soekandar Mojokerto",
  "Rumah Sakit Umum Daerah (RSUD) Kabupaten Jombang": "RSUD Jombang",
  "RSUD Gambiran": "RSUD Gambiran Kota Kediri",
  "RSUD Ngudi Waluyo Wlingi Kabupaten Blitar": "RSUD Ngudi Waluyo Wlingi Blitar",
  "RSD Nganjuk": "RSUD Nganjuk",
  "RSUD Dokter Harjono Kabupaten Ponorogo": "RSUD dr. Harjono Ponorogo",
  "Rumah Sakit Umum Daerah (RSUD) dr. Soeroto Ngawi": "RSUD dr. Soeroto Ngawi",
  "RSUD Dr.Sosodoro Bojonegoro": "RSUD dr. R. Sosodoro Djatikoesoemo Bojonegoro",
  "RSUD Dr. R. Koesma Kabupaten Tuban": "RSUD dr. R. Koesma Tuban",
  "Rumah Sakit Muhammadiyah Lamongan": "RS Muhammadiyah Lamongan",
  "RSUD Bangil Kabupaten Pasuruan": "RSUD Bangil Pasuruan",
  "Rumah Sakit Umum Daerah dr. R. Soedarsono Kota Pasuruan": "RSUD dr. R. Soedarsono Kota Pasuruan",
  "RSUD dr. Mohamad Saleh": "RSUD dr. Mohamad Saleh Probolinggo",
  "RSUD Waluyo Jati": "RSUD Waluyo Jati Kraksaan Probolinggo",
  "Rumah Sakit Umum Daerah (RSUD) dr. Haryoto": "RSUD dr. Haryoto Lumajang",
  "Rumah Sakit Daerah (RSD) dr. Soebandi": "RSD dr. Soebandi Jember",
  "Rumah Sakit Citra Husada Jember": "RS Citra Husada Jember",
  "RSUD dr. Abdoer Rahem - Situbondo": "RSUD dr. Abdoer Rahem Situbondo",
  "Rumah Sakit Umum Daerah Blambangan": "RSUD Blambangan Banyuwangi",
  "RSUD Genteng - Banyuwangi": "RSUD Genteng Banyuwangi",
  "UOBK RSUD Syarifah Ambami Rato Ebu Bangkalan": "RSUD Syarifah Ambami Rato Ebu Bangkalan",
  "RSUD dr. Mohammad Zyn Kabupaten Sampang": "RSUD dr. Mohammad Zyn Sampang",
  "RSUD Dr. H. Slamet Martodirdjo (RSUD Smart) Pamekasan": "RSUD dr. H. Slamet Martodirdjo Pamekasan",
};

// Phones missing from Google Places, filled from other public sources (verify before relying on them).
const PHONES = {
  "RSUD Blambangan Banyuwangi": "(0333) 421118",
};
// Same, keyed by Places id (sources: hospital websites / official documents).
const PHONES_BY_PLACE = {
  "ChIJbxPSSxXxaS4RBPKLUsyhd34": "(021) 7219000",  // RS Pusat Pertamina
  "ChIJ8Ti-K9SKQS4RKHFq-Wv6Deg": "(0254) 200528",  // RSUD dr. Dradjat Prawiranegara (RBA RSDP 2026)
  "ChIJfz5XUofJei4Rar4pmKSbFqU": "(0287) 3873318", // RSUD dr. Soedirman Kebumen (dokumen RSUD)
  "ChIJb1fZypNYei4RvZBcSXuDGgw": "(0274) 4530303", // RS Akademik UGM - IGD 24 jam (rsa.ugm.ac.id)
};

// Generic tidy-up for names without a manual override.
const tidyName = n => n
  .replace(/^UOBK\s+/i, "")
  .replace(/Rumah Sakit Umum Pusat Nasional/gi, "RSUPN")
  .replace(/Rumah Sakit Umum Pusat/gi, "RSUP")
  .replace(/Rumah Sakit Umum Daerah/gi, "RSUD")
  .replace(/Rumah Sakit Umum/gi, "RSU")
  .replace(/Rumah Sakit/gi, "RS")
  .replace(/\(RSUD\)\s*/g, "")
  .replace(/\s+-\s+/g, " ")
  .replace(/\s+/g, " ")
  .trim();

const cleanAddress = a => a
  .replace(/^[A-Z0-9]{4}\+[A-Z0-9]{2,3},\s*/, "") // leading Google plus code
  .replace(/^Jalan,\s*/, "")
  .replace(/^RS Semen Gresik,\s*/, "")
  .replace(/\s+/g, " ")
  .trim();

const seed = raw.filter(h => !h.error && h.isHospital && h.businessStatus === "OPERATIONAL").map(h => {
  const nama = NAMES[h.nama] || NAMES[tidyName(h.nama)] || tidyName(h.nama);
  return {
    nama,
    alamat: cleanAddress(h.alamat),
    telepon: h.telepon || PHONES_BY_PLACE[h.placeId] || PHONES[nama] || "-",
    latitude: Math.round(h.latitude * 1e7) / 1e7,
    longitude: Math.round(h.longitude * 1e7) / 1e7,
    wilayah: h.area,
    provinsi: h.province,
    placeId: h.placeId,
  };
});

const outDir = path.join(__dirname, "../app/src/main/assets");
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, "hospitals_seed.json"), JSON.stringify(seed, null, 2) + "\n");
console.log(`wrote ${seed.length} hospitals to app/src/main/assets/hospitals_seed.json`);
const missingPhone = seed.filter(h => h.telepon === "-").map(h => h.nama);
if (missingPhone.length) console.log("missing phone:", missingPhone.join(", "));
