// Fetches name, address, phone and coordinates of major hospitals in the rest of Java
// (DKI Jakarta, Banten, Jawa Barat, Jawa Tengah, DI Yogyakarta) from the Google Places API (New)
// Text Search, and writes scripts/hospitals_jawa.json. East Java lives in fetch_hospitals_jatim.js.
//
// Usage (from the repo root):  node scripts/fetch_hospitals_jawa.js
// The API key is read from local.properties (PLACES_API_KEY, or MAPS_API_KEY as fallback).
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
// Key is read from local.properties (gitignored): PLACES_API_KEY if set, otherwise MAPS_API_KEY.
const localProps = fs.readFileSync(path.join(root, "local.properties"), "utf8");
const prop = name => (localProps.match(new RegExp("^" + name + "=(.+)$", "m")) || [])[1]?.trim();
const KEY = prop("PLACES_API_KEY") || prop("MAPS_API_KEY");
if (!KEY) throw new Error("Set PLACES_API_KEY or MAPS_API_KEY in local.properties");

// province -> [query, kota/kabupaten]
const QUERIES = {
  "DKI Jakarta": [
    ["RSUPN Dr. Cipto Mangunkusumo Jakarta", "Jakarta Pusat"],
    ["RSUP Fatmawati Jakarta", "Jakarta Selatan"],
    ["RSUP Persahabatan Jakarta", "Jakarta Timur"],
    ["RSPAD Gatot Soebroto Jakarta", "Jakarta Pusat"],
    ["RS Jantung dan Pembuluh Darah Harapan Kita", "Jakarta Barat"],
    ["RS Kanker Dharmais Jakarta", "Jakarta Barat"],
    ["RS Pusat Otak Nasional Prof Mahar Mardjono", "Jakarta Timur"],
    ["RSUD Tarakan Jakarta", "Jakarta Pusat"],
    ["RSUD Pasar Minggu Jakarta", "Jakarta Selatan"],
    ["RSUD Koja Jakarta", "Jakarta Utara"],
    ["RSUD Cengkareng Jakarta", "Jakarta Barat"],
    ["RSUD Budhi Asih Jakarta", "Jakarta Timur"],
    ["RSUD Pasar Rebo Jakarta", "Jakarta Timur"],
    ["RS Bhayangkara Tk. I Pusdokkes Polri Kramat Jati", "Jakarta Timur"],
    ["RS Pusat Pertamina Jakarta", "Jakarta Selatan"],
    ["RS Islam Jakarta Cempaka Putih", "Jakarta Pusat"],
    ["RS Pondok Indah Jakarta", "Jakarta Selatan"],
    ["RS Medistra Jakarta", "Jakarta Selatan"],
    ["Siloam Hospitals Kebon Jeruk", "Jakarta Barat"],
    ["Mayapada Hospital Lebak Bulus", "Jakarta Selatan"],
    ["RS Mitra Keluarga Kelapa Gading", "Jakarta Utara"],
    ["RS Hermina Jatinegara", "Jakarta Timur"],
  ],
  "Banten": [
    ["RSUD Kota Tangerang", "Kota Tangerang"],
    ["RSUD Kabupaten Tangerang", "Kabupaten Tangerang"],
    ["Siloam Hospitals Lippo Village Karawaci", "Kabupaten Tangerang"],
    ["RSU Kota Tangerang Selatan", "Kota Tangerang Selatan"],
    ["RS Premier Bintaro", "Kota Tangerang Selatan"],
    ["Eka Hospital BSD", "Kota Tangerang Selatan"],
    ["RSUD Banten Serang", "Kota Serang"],
    ["RSUD dr. Dradjat Prawiranegara Serang", "Kabupaten Serang"],
    ["RSUD Kota Cilegon", "Kota Cilegon"],
    ["RS Krakatau Medika Cilegon", "Kota Cilegon"],
    ["RSUD dr. Adjidarmo Rangkasbitung", "Kabupaten Lebak"],
    ["RSUD Berkah Pandeglang", "Kabupaten Pandeglang"],
  ],
  "Jawa Barat": [
    ["RSUP Dr. Hasan Sadikin Bandung", "Kota Bandung"],
    ["RS Santo Borromeus Bandung", "Kota Bandung"],
    ["RS Advent Bandung", "Kota Bandung"],
    ["RS Al Islam Bandung", "Kota Bandung"],
    ["RS Immanuel Bandung", "Kota Bandung"],
    ["Santosa Hospital Bandung Central", "Kota Bandung"],
    ["RSUD Kota Bandung Ujungberung", "Kota Bandung"],
    ["RSUD Al Ihsan Baleendah", "Kabupaten Bandung"],
    ["RSUD Cibabat Cimahi", "Kota Cimahi"],
    ["RS Dustira Cimahi", "Kota Cimahi"],
    ["RSUD Kota Bogor", "Kota Bogor"],
    ["RS PMI Bogor", "Kota Bogor"],
    ["RSUD Cibinong", "Kabupaten Bogor"],
    ["RSUD Ciawi Bogor", "Kabupaten Bogor"],
    ["RSUD Kota Depok", "Kota Depok"],
    ["Rumah Sakit Universitas Indonesia Depok", "Kota Depok"],
    ["RSUD dr. Chasbullah Abdulmadjid Kota Bekasi", "Kota Bekasi"],
    ["RS Mitra Keluarga Bekasi Barat", "Kota Bekasi"],
    ["RSUD Kabupaten Bekasi Cibitung", "Kabupaten Bekasi"],
    ["RSUD Karawang", "Kabupaten Karawang"],
    ["RSUD Bayu Asih Purwakarta", "Kabupaten Purwakarta"],
    ["RSUD Subang", "Kabupaten Subang"],
    ["RSUD Sumedang", "Kabupaten Sumedang"],
    ["RSUD Sayang Cianjur", "Kabupaten Cianjur"],
    ["RSUD R. Syamsudin SH Sukabumi", "Kota Sukabumi"],
    ["RSUD dr. Slamet Garut", "Kabupaten Garut"],
    ["RSUD dr. Soekardjo Tasikmalaya", "Kota Tasikmalaya"],
    ["RSUD Ciamis", "Kabupaten Ciamis"],
    ["RSUD Kota Banjar", "Kota Banjar"],
    ["RSUD Pandega Pangandaran", "Kabupaten Pangandaran"],
    ["RSUD Gunung Jati Cirebon", "Kota Cirebon"],
    ["RSUD Waled Cirebon", "Kabupaten Cirebon"],
    ["RSUD Indramayu", "Kabupaten Indramayu"],
    ["RSUD Majalengka", "Kabupaten Majalengka"],
    ["RSUD 45 Kuningan", "Kabupaten Kuningan"],
  ],
  "Jawa Tengah": [
    ["RSUP Dr. Kariadi Semarang", "Kota Semarang"],
    ["RSUD K.R.M.T. Wongsonegoro Semarang", "Kota Semarang"],
    ["RSUD Tugurejo Semarang", "Kota Semarang"],
    ["RS Telogorejo Semarang", "Kota Semarang"],
    ["RS St. Elisabeth Semarang", "Kota Semarang"],
    ["RS Islam Sultan Agung Semarang", "Kota Semarang"],
    ["RS Roemani Muhammadiyah Semarang", "Kota Semarang"],
    ["RSUD Ungaran", "Kabupaten Semarang"],
    ["RSUD Kota Salatiga", "Kota Salatiga"],
    ["RSUD dr. H. Soewondo Kendal", "Kabupaten Kendal"],
    ["RSUD Sunan Kalijaga Demak", "Kabupaten Demak"],
    ["RSUD dr. R. Soedjati Soemodiardjo Purwodadi", "Kabupaten Grobogan"],
    ["RSUD RA Kartini Jepara", "Kabupaten Jepara"],
    ["RSUD dr. Loekmono Hadi Kudus", "Kabupaten Kudus"],
    ["RSUD RAA Soewondo Pati", "Kabupaten Pati"],
    ["RSUD dr. R. Soetrasno Rembang", "Kabupaten Rembang"],
    ["RSUD dr. R. Soetijono Blora", "Kabupaten Blora"],
    ["RSUD Dr. Moewardi Surakarta", "Kota Surakarta"],
    ["RS Dr. Oen Kandang Sapi Solo", "Kota Surakarta"],
    ["RS PKU Muhammadiyah Surakarta", "Kota Surakarta"],
    ["RSUD Ir. Soekarno Sukoharjo", "Kabupaten Sukoharjo"],
    ["RSUD Karanganyar", "Kabupaten Karanganyar"],
    ["RSUD dr. Soehadi Prijonegoro Sragen", "Kabupaten Sragen"],
    ["RSUD dr. Soediran Mangun Sumarso Wonogiri", "Kabupaten Wonogiri"],
    ["RSUD Pandan Arang Boyolali", "Kabupaten Boyolali"],
    ["RSUP dr. Soeradji Tirtonegoro Klaten", "Kabupaten Klaten"],
    ["RSUD Tidar Kota Magelang", "Kota Magelang"],
    ["RST dr. Soedjono Magelang", "Kota Magelang"],
    ["RSUD Muntilan Magelang", "Kabupaten Magelang"],
    ["RSUD Djojonegoro Temanggung", "Kabupaten Temanggung"],
    ["RSUD KRT Setjonegoro Wonosobo", "Kabupaten Wonosobo"],
    ["RSUD dr. Tjitrowardojo Purworejo", "Kabupaten Purworejo"],
    ["RSUD dr. Soedirman Kebumen", "Kabupaten Kebumen"],
    ["RSUD Prof. Dr. Margono Soekarjo Purwokerto", "Kabupaten Banyumas"],
    ["RSUD Banyumas", "Kabupaten Banyumas"],
    ["RSUD Cilacap", "Kabupaten Cilacap"],
    ["RSUD dr. R. Goeteng Taroenadibrata Purbalingga", "Kabupaten Purbalingga"],
    ["RSUD Hj. Anna Lasmanah Banjarnegara", "Kabupaten Banjarnegara"],
    ["RSUD Kardinah Tegal", "Kota Tegal"],
    ["RSUD dr. Soeselo Slawi", "Kabupaten Tegal"],
    ["RSUD Brebes", "Kabupaten Brebes"],
    ["RSUD dr. M. Ashari Pemalang", "Kabupaten Pemalang"],
    ["RSUD Bendan Pekalongan", "Kota Pekalongan"],
    ["RSUD Kraton Pekalongan", "Kabupaten Pekalongan"],
    ["RSUD Batang", "Kabupaten Batang"],
  ],
  "DI Yogyakarta": [
    ["RSUP Dr. Sardjito Yogyakarta", "Kabupaten Sleman"],
    ["RS Panti Rapih Yogyakarta", "Kota Yogyakarta"],
    ["RS Bethesda Yogyakarta", "Kota Yogyakarta"],
    ["RS PKU Muhammadiyah Yogyakarta", "Kota Yogyakarta"],
    ["RSUD Kota Yogyakarta", "Kota Yogyakarta"],
    ["RS Akademik UGM", "Kabupaten Sleman"],
    ["RS JIH Yogyakarta", "Kabupaten Sleman"],
    ["RSUD Sleman", "Kabupaten Sleman"],
    ["RSUD Panembahan Senopati Bantul", "Kabupaten Bantul"],
    ["RSUD Wates Kulon Progo", "Kabupaten Kulon Progo"],
    ["RSUD Wonosari Gunungkidul", "Kabupaten Gunungkidul"],
  ],
};

// Words that must appear in the Places address for each province (sanity check).
const PROVINCE_MATCH = {
  "DKI Jakarta": /Jakarta/i,
  "Banten": /Banten/i,
  "Jawa Barat": /Jawa Barat/i,
  "Jawa Tengah": /Jawa Tengah/i,
  "DI Yogyakarta": /Yogyakarta/i,
};

// Java west of East Java.
const JAWA = { low: { latitude: -8.4, longitude: 105.0 }, high: { latitude: -5.8, longitude: 111.75 } };

async function search(query) {
  const res = await fetch("https://places.googleapis.com/v1/places:searchText", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": KEY,
      "X-Goog-FieldMask": "places.id,places.displayName,places.formattedAddress,places.location,places.nationalPhoneNumber,places.businessStatus,places.types",
    },
    body: JSON.stringify({ textQuery: query, languageCode: "id", maxResultCount: 1, locationRestriction: { rectangle: JAWA } }),
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`${query}: ${JSON.stringify(json)}`);
  return (json.places || [])[0];
}

(async () => {
  const out = [];
  for (const [province, list] of Object.entries(QUERIES)) {
    for (const [query, area] of list) {
      const p = await search(query);
      if (!p) { out.push({ query, area, province, error: "no result" }); continue; }
      const alamat = (p.formattedAddress || "").replace(/, Indonesia$/, "");
      out.push({
        query,
        area,
        province,
        placeId: p.id,
        nama: p.displayName?.text || "",
        alamat,
        telepon: p.nationalPhoneNumber || "",
        latitude: p.location?.latitude,
        longitude: p.location?.longitude,
        businessStatus: p.businessStatus || "",
        isHospital: (p.types || []).includes("hospital"),
        provinceOk: PROVINCE_MATCH[province].test(alamat),
      });
    }
  }
  fs.writeFileSync(path.join(__dirname, "hospitals_jawa.json"), JSON.stringify(out, null, 2));
  console.log(`saved ${out.length} entries`);
})();
