/** Demo fitment tree — maker → model → generation → engine (cascading UI). */
export type VehicleEngine = { id: string; label: string };
export type VehicleGeneration = {
  id: string;
  label: string;
  engines: VehicleEngine[];
};
export type VehicleModel = {
  id: string;
  label: string;
  generations: VehicleGeneration[];
};
export type VehicleMaker = {
  id: string;
  label: string;
  models: VehicleModel[];
};

export const VEHICLE_CATALOG: VehicleMaker[] = [
  {
    id: "nissan",
    label: "Nissan",
    models: [
      {
        id: "navara",
        label: "Navara",
        generations: [
          {
            id: "d40",
            label: "D40 (2005–2015)",
            engines: [
              { id: "yd25", label: "YD25DDTi 2.5 Diesel" },
              { id: "vq40", label: "VQ40DE 4.0 Petrol" },
            ],
          },
          {
            id: "d23",
            label: "D23 NP300 (2015–)",
            engines: [
              { id: "ys23", label: "YS23DDTT 2.3 Diesel" },
              { id: "qr25-d23", label: "QR25DE 2.5 Petrol" },
            ],
          },
        ],
      },
      {
        id: "xtrail",
        label: "X-Trail",
        generations: [
          {
            id: "t31",
            label: "T31 (2007–2013)",
            engines: [
              { id: "qr25-t31", label: "QR25DE 2.5 Petrol" },
              { id: "yd25-t31", label: "YD25DDTi 2.5 Diesel" },
            ],
          },
          {
            id: "t32",
            label: "T32 (2013–2021)",
            engines: [
              { id: "mr20", label: "MR20DD 2.0 Petrol" },
              { id: "qr25-t32", label: "QR25DE 2.5 Petrol" },
              { id: "r9m", label: "R9M 1.6 Diesel" },
            ],
          },
        ],
      },
      {
        id: "patrol",
        label: "Patrol",
        generations: [
          {
            id: "y61",
            label: "Y61 (1997–2016)",
            engines: [
              { id: "td42", label: "TD42 4.2 Diesel" },
              { id: "zd30", label: "ZD30DDTi 3.0 Diesel" },
              { id: "tb48", label: "TB48DE 4.8 Petrol" },
            ],
          },
          {
            id: "y62",
            label: "Y62 (2010–)",
            engines: [
              { id: "vk56", label: "VK56VD 5.6 Petrol" },
              { id: "ys26", label: "YS26DDTT 2.8 Diesel" },
            ],
          },
        ],
      },
      {
        id: "qashqai",
        label: "Qashqai",
        generations: [
          {
            id: "j10",
            label: "J10 (2006–2013)",
            engines: [
              { id: "hr16", label: "HR16DE 1.6 Petrol" },
              { id: "mr20-j10", label: "MR20DE 2.0 Petrol" },
            ],
          },
          {
            id: "j11",
            label: "J11 (2013–2021)",
            engines: [
              { id: "hr16-j11", label: "HR16DE 1.6 Petrol" },
              { id: "mr20-j11", label: "MR20DD 2.0 Petrol" },
            ],
          },
        ],
      },
      {
        id: "np200",
        label: "NP200",
        generations: [
          {
            id: "np200-1",
            label: "NP200 (2008–)",
            engines: [
              { id: "k7m", label: "K7M 1.6 Petrol" },
              { id: "k9k", label: "K9K 1.5 Diesel" },
            ],
          },
        ],
      },
    ],
  },
  {
    id: "infiniti",
    label: "Infiniti",
    models: [
      {
        id: "q50",
        label: "Q50",
        generations: [
          {
            id: "v37",
            label: "V37 (2013–)",
            engines: [
              { id: "vr30", label: "VR30DDTT 3.0 Petrol" },
              { id: "vq37", label: "VQ37VHR 3.7 Petrol" },
            ],
          },
        ],
      },
      {
        id: "fx",
        label: "FX / QX70",
        generations: [
          {
            id: "s51",
            label: "S51 (2008–2017)",
            engines: [
              { id: "vq37-fx", label: "VQ37VHR 3.7 Petrol" },
              { id: "vk50", label: "VK50VE 5.0 Petrol" },
            ],
          },
        ],
      },
    ],
  },
];
