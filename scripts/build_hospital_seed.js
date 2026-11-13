// Cleans scripts/hospitals_jatim.json (raw Google Places results) into the seed file the app
// imports: app/src/main/assets/hospitals_jatim.json.
// Usage (from repo root):  node scripts/build_hospital_seed.js
const fs = require("fs");
const path = require("path");

const raw = JSON.parse(fs.readFileSync(path.join(__dirname, "hospitals_jatim.json"), "utf8"));

// Consistent display names ("<type> <name> <city>") instead of the mixed Google listing titles.
const NAMES = {
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

const cleanAddress = a => a
  .replace(/^[A-Z0-9]{4}\+[A-Z0-9]{2,3},\s*/, "") // leading Google plus code
  .replace(/^Jalan,\s*/, "")
  .replace(/^RS Semen Gresik,\s*/, "")
  .replace(/\s+/g, " ")
  .trim();

const seed = raw.filter(h => !h.error && h.isHospital && h.businessStatus === "OPERATIONAL").map(h => {
  const nama = NAMES[h.nama] || h.nama;
  return {
    nama,
    alamat: cleanAddress(h.alamat),
    telepon: h.telepon || PHONES[nama] || "-",
    latitude: Math.round(h.latitude * 1e7) / 1e7,
    longitude: Math.round(h.longitude * 1e7) / 1e7,
    wilayah: h.area,
    placeId: h.placeId,
  };
});

const outDir = path.join(__dirname, "../app/src/main/assets");
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, "hospitals_jatim.json"), JSON.stringify(seed, null, 2) + "\n");
console.log(`wrote ${seed.length} hospitals to app/src/main/assets/hospitals_jatim.json`);
const missingPhone = seed.filter(h => h.telepon === "-").map(h => h.nama);
if (missingPhone.length) console.log("missing phone:", missingPhone.join(", "));
