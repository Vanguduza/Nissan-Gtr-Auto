import Link from "next/link";
import {
  ClipboardList,
  Columns2,
  Gift,
  Heart,
  iconSizeMd,
  iconSizeSm,
  iconStroke,
  LayoutGrid,
  MapPin,
  MessageCircle,
  RotateCcw,
  Smartphone,
  Star,
  Car,
  UserRound,
  type LucideIcon,
} from "@/components/icons";
import styles from "@/components/account.module.css";

const nav: {
  href: string;
  label: string;
  exact?: boolean;
  Icon: LucideIcon;
}[] = [
  { href: "/account", label: "Overview", exact: true, Icon: LayoutGrid },
  { href: "/account/profile", label: "Personal details", Icon: UserRound },
  { href: "/account/addresses", label: "Addresses", Icon: MapPin },
  { href: "/account/garage", label: "My Garage", Icon: Car },
  { href: "/account/orders", label: "Orders & tracking", Icon: ClipboardList },
  { href: "/account/chat", label: "Live chat", Icon: MessageCircle },
  { href: "/account/wishlist", label: "Wishlist", Icon: Heart },
  { href: "/account/returns", label: "Returns", Icon: RotateCcw },
  { href: "/account/compare", label: "Compare", Icon: Columns2 },
  { href: "/account/loyalty", label: "Loyalty", Icon: Gift },
  { href: "/account/reviews", label: "My reviews", Icon: Star },
  { href: "/account/apps", label: "Mobile apps", Icon: Smartphone },
];

export function AccountNav({ current }: { current: string }) {
  return (
    <nav className={styles.nav} aria-label="My Account">
      <p className={styles.navTitle}>My Account</p>
      <ul className={styles.navList}>
        {nav.map((item) => {
          const active = item.exact
            ? current === item.href
            : current === item.href || current.startsWith(item.href + "/");
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={active ? styles.navLinkActive : styles.navLink}
              >
                <item.Icon
                  size={iconSizeSm}
                  strokeWidth={iconStroke}
                  aria-hidden
                />
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

export const accountCardIcons: Record<string, LucideIcon> = {
  "/account/profile": UserRound,
  "/account/addresses": MapPin,
  "/account/garage": Car,
  "/account/orders": ClipboardList,
  "/account/chat": MessageCircle,
  "/account/wishlist": Heart,
  "/account/returns": RotateCcw,
  "/account/compare": Columns2,
  "/account/loyalty": Gift,
  "/account/reviews": Star,
  "/account/apps": Smartphone,
};

export { iconSizeMd };
