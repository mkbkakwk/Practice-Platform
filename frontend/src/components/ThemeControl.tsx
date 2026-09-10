import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuLabel, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";

const options = [
  { value: "light", label: "浅色", Icon: Sun },
  { value: "dark", label: "深色", Icon: Moon },
  { value: "system", label: "跟随系统", Icon: Monitor },
] as const;

export function ThemeControl({ className }: { className?: string }) {
  const { theme, resolvedTheme, setTheme } = useTheme();
  const current = options.find((option) => option.value === theme) ?? options[1];
  const CurrentIcon = resolvedTheme === "light" ? Sun : resolvedTheme === "dark" ? Moon : current.Icon;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className={className} aria-label={`切换主题，当前${current.label}`} title={`主题：${current.label}`}>
          <CurrentIcon className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="graphite-theme w-40" align="end" aria-label="主题选择">
        <DropdownMenuLabel>外观主题</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={theme ?? "dark"} onValueChange={setTheme} aria-label="选择外观主题">
          {options.map(({ value, label, Icon }) => (
            <DropdownMenuRadioItem key={value} value={value}>
              <Icon className="h-4 w-4" />
              {label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
